package com.devtunde.authservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.devtunde.authservice.config.RefreshProperties;
import com.devtunde.authservice.exception.InvalidAccessTokenException;
import com.devtunde.authservice.exception.InvalidRefreshTokenException;
import com.devtunde.authservice.model.User;
import io.jsonwebtoken.Claims;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RefreshTokenService {

    public static final String TOKEN_KEY_PREFIX = "auth:refresh:token:";
    public static final String FAMILY_KEY_PREFIX = "auth:refresh:family:";
    public static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final UserService userService;
    private final StringRedisTemplate redis;
    private final RefreshProperties refresh;
    private final SecureRandom random = new SecureRandom();
    private final AuthAudit authAudit;

    public RefreshTokenService(
            StringRedisTemplate redis, RefreshProperties refresh, UserService userService, AuthAudit authAudit) {
        this.redis = redis;
        this.refresh = refresh;
        this.userService = userService;
        this.authAudit = authAudit;
    }

    public String issue(User user) {

        Instant now = Instant.now();

        String familyId = UUID.randomUUID().toString();
        String token = randomToken();
        String hash = sha256Hex(token);

        String record = """
                {
                    "userId":"%s",
                    "familyId":"%s",
                    "createdAt":"%s",
                    "lastRotatedAt":"%s",
                    "absoluteExpiry":"%s"
                }
            """.formatted(user.getId(), familyId, now, now, now.plus(refresh.absoluteTtl()));

        redis.opsForValue().set(TOKEN_KEY_PREFIX + hash, record, refresh.rollingTtl());
        redis.opsForValue().set(familyKey(user.getId(), familyId), hash, refresh.absoluteTtl());

        return token;
    }

    public record Rotation(String refreshToken, User user) {}

    public Rotation rotate(String presentedToken) {

        Instant now = Instant.now();
        String hash = sha256Hex(presentedToken);

        String json = redis.opsForValue().get(TOKEN_KEY_PREFIX + hash);
        if (json == null) {
            throw new InvalidRefreshTokenException("Unknown refresh token");
        }

        JsonNode record = readRecord(json);
        UUID userId = UUID.fromString(record.get("userId").asString());
        String familyId = record.get("familyId").asString();
        String familyKey = familyKey(userId, familyId);

        if (!hash.equals(redis.opsForValue().get(familyKey))) {
            String currentHash = redis.opsForValue().get(familyKey);
            redis.delete(List.of(familyKey, TOKEN_KEY_PREFIX + hash, TOKEN_KEY_PREFIX + currentHash));

            authAudit.log("refresh_reuse_detected", userId, "reused_rotated_token", "failure");

            throw new InvalidRefreshTokenException("Refresh token reuse detected");
        }

        Instant lastRotatedAt = Instant.parse(record.get("lastRotatedAt").asString());
        Instant absoluteExpiry = Instant.parse(record.get("absoluteExpiry").asString());

        if (now.isAfter(lastRotatedAt.plus(refresh.rollingTtl()))) {
            redis.delete(List.of(familyKey, TOKEN_KEY_PREFIX + hash));

            authAudit.log("refresh_expired", userId, "rolling_window_elapsed", "failure");

            throw new InvalidRefreshTokenException("Refresh token expired");
        }

        if (now.isAfter(absoluteExpiry)) {
            redis.delete(List.of(familyKey, TOKEN_KEY_PREFIX + hash));

            authAudit.log("refresh_expired", userId, "absolute_cap_elapsed", "failure");

            throw new InvalidRefreshTokenException("Refresh token expired");
        }

        String newToken = randomToken();
        String newHash = sha256Hex(newToken);
        String newRecord =
                """
                {
                    "userId":"%s",
                    "familyId":"%s",
                    "createdAt":"%s",
                    "lastRotatedAt":"%s",
                    "absoluteExpiry":"%s"
                }
            """.formatted(userId, familyId, record.get("createdAt").asString(), now, absoluteExpiry);

        redis.opsForValue().set(TOKEN_KEY_PREFIX + newHash, newRecord, refresh.rollingTtl());
        redis.opsForValue().set(familyKey, newHash, refresh.absoluteTtl());

        User user = userService
                .findById(userId)
                .orElseThrow(() -> new InvalidRefreshTokenException("Token owner no longer exists"));

        return new Rotation(newToken, user);
    }

    public void blacklist(String jti, Duration remainingLife) {

        redis.opsForValue().set(BLACKLIST_PREFIX + jti, "revoked", remainingLife);
    }

    public boolean isBlacklisted(String jti) {

        return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + jti));
    }

    public void revokeByToken(String rawToken) {

        String hash = sha256Hex(rawToken);
        String json = redis.opsForValue().get(TOKEN_KEY_PREFIX + hash);
        if (json == null) {
            return;
        }

        JsonNode record = readRecord(json);
        UUID userId = UUID.fromString(record.get("userId").asString());
        String familyId = record.get("familyId").asString();

        redis.delete(List.of(familyKey(userId, familyId), TOKEN_KEY_PREFIX + hash));
    }

    public void logout(Claims claims, String refreshToken) {

        String jti = claims.getId();
        UUID userId = UUID.fromString(claims.getSubject());

        if (isBlacklisted(jti)) {
            authAudit.log("logout_failure", userId, "token_already_revoked", "failure");
            throw new InvalidAccessTokenException("Invalid access token");
        }

        Duration remaining =
                Duration.between(Instant.now(), claims.getExpiration().toInstant());
        if (!remaining.isNegative() && !remaining.isZero()) {
            blacklist(jti, remaining);
        }

        if (refreshToken != null) {
            revokeByToken(refreshToken);
        }

        authAudit.log("logout", userId, "ok", "success");
    }

    public void revokeAllSessions(UUID userId) {

        Set<String> keysToDelete = new HashSet<>();

        try (var cursor = redis.scan(ScanOptions.scanOptions()
                .match(FAMILY_KEY_PREFIX + userId + ":*")
                .build())) {
            cursor.forEachRemaining(familyKey -> {
                String currentHash = redis.opsForValue().get(familyKey);
                keysToDelete.add(familyKey);
                if (currentHash != null) {
                    keysToDelete.add(TOKEN_KEY_PREFIX + currentHash);
                }
            });
        }

        if (!keysToDelete.isEmpty()) {
            redis.delete(keysToDelete);
        }

        authAudit.log("revoke_all_sessions", userId, "user_requested_or_forced", "success");
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);

        return HexFormat.of().formatHex(bytes);
    }

    private static String familyKey(UUID userId, String familyId) {

        return FAMILY_KEY_PREFIX + userId + ":" + familyId;
    }

    private static String sha256Hex(String value) {

        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private JsonNode readRecord(String json) {

        return JsonMapper.shared().readTree(json);
    }
}
