package com.devtunde.authservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devtunde.authservice.config.ResetProperties;
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
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(
            StringRedisTemplate redis, UserService userService, ResetEmailSender emailSender, ResetProperties reset) {

        this.redis = redis;
        this.userService = userService;
        this.emailSender = emailSender;
        this.reset = reset;
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
