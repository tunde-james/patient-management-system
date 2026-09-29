package com.devtunde.authservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devtunde.authservice.config.ResetProperties;
import com.devtunde.authservice.exception.BreachedPasswordException;
import com.devtunde.authservice.exception.InvalidResetTokenException;
import com.devtunde.authservice.model.User;

@Service
public class PasswordResetService {

    public static final String TOKEN_KEY_PREFIX = "auth:reset:token:";
    public static final String USER_POINTER_PREFIX = "auth:reset:user:";

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final StringRedisTemplate redis;
    private final UserService userService;
    private final ResetEmailSender emailSender;
    private final ResetProperties reset;
    private final RefreshTokenService refreshTokenService;
    private final AuthAudit auAuthAudit;
    BreachedPasswordChecker breachedPasswordChecker;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(
            StringRedisTemplate redis,
            UserService userService,
            ResetEmailSender emailSender,
            ResetProperties reset,
            RefreshTokenService refreshTokenService,
            AuthAudit auAuthAudit,
            BreachedPasswordChecker breachedPasswordChecker) {
        this.redis = redis;
        this.userService = userService;
        this.emailSender = emailSender;
        this.reset = reset;
        this.refreshTokenService = refreshTokenService;
        this.auAuthAudit = auAuthAudit;
        this.breachedPasswordChecker = breachedPasswordChecker;
    }

    public void requestReset(String email) {

        Optional<User> candidate = userService.findByEmail(email);
        if (candidate.isEmpty() || !candidate.get().isEnabled()) {
            return;
        }

        User user = candidate.get();

        String token = randomHexToken();
        String hash = sha256Hex(token);

        String userPointer = USER_POINTER_PREFIX + user.getId();
        String previousHash = redis.opsForValue().get(userPointer);
        if (previousHash != null) {
            redis.delete(TOKEN_KEY_PREFIX + previousHash);
        }

        redis.opsForValue().set(TOKEN_KEY_PREFIX + hash, user.getId().toString(), reset.tokenTtl());
        redis.opsForValue().set(userPointer, hash, reset.tokenTtl());

        try {
            emailSender.send(email, reset.baseUrl() + "/reset-password?token=" + token);
        } catch (Exception ex) {
            log.warn("password-reset email failed to send for user {}", user.getId());
        }
    }

    public void completeReset(String rawToken, String newPassword) {

        if (breachedPasswordChecker.isBreached(newPassword)) {
            throw new BreachedPasswordException("This password has appeared in a data breach; choose another.");
        }

        String hash = sha256Hex(rawToken);
        String userId = redis.opsForValue().getAndDelete(TOKEN_KEY_PREFIX + hash);

        if (userId == null) {
            throw new InvalidResetTokenException("Invalid reset token");
        }

        redis.delete(USER_POINTER_PREFIX + userId);

        userService.updatePassword(UUID.fromString(userId), newPassword);
        refreshTokenService.revokeAllSessions(UUID.fromString(userId));

        auAuthAudit.log("password_reset_completed", userId, "reset_token_verified", "success");
    }

    private String randomHexToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
