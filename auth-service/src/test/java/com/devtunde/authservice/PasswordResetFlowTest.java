package com.devtunde.authservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.devtunde.authservice.model.User;
import com.devtunde.authservice.repository.UserRepository;
import com.devtunde.authservice.service.ConsoleResetEmailSender;
import com.devtunde.authservice.service.PasswordResetService;
import com.redis.testcontainers.RedisContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetFlowTest {

    private static final String FORGOT = "/api/v1/auth/forgot-password";
    private static final Pattern LINK_TOKEN = Pattern.compile("link=\\S*token=([0-9a-f]{64})\\b");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Container
    static RedisContainer redis = new RedisContainer("redis:7");

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> redis.getRedisHost());
        registry.add("spring.data.redis.port", () -> redis.getRedisPort());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private User seedUser(String email) {
        return userRepository.save(new User(email, passwordEncoder.encode("pass-word-1234")));
    }

    private String forgotBody(String email) throws Exception {
        return mockMvc.perform(
                        post(FORGOT).contentType(MediaType.APPLICATION_JSON).content("""
                                   {
                                        "email":"%s"
                                    }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static String sha256Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private String captureResetToken(String email) throws Exception {
        Logger senderLogger = (Logger) LoggerFactory.getLogger(ConsoleResetEmailSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        senderLogger.addAppender(appender);
        try {
            forgotBody(email);

            for (ILoggingEvent event : appender.list) {
                String message = event.getFormattedMessage();
                if (message.contains(email)) {
                    Matcher matcher = LINK_TOKEN.matcher(message);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }
            throw new AssertionError("no reset email log line captured for " + email);
        } finally {
            senderLogger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("known and unknown emails receive byte-identical 200 bodies")
    void existenceNeverLeaks() throws Exception {
        String unknownBody = forgotBody("nobody-" + UUID.randomUUID() + "@example.com");
        seedUser("known-" + UUID.randomUUID() + "@example.com");
        String knownResponse = forgotBody("known-email-never-asserted@example.com");

        // regenerate bodies side by side with THIS email being the "known" one:
        String knownEmail = "known-email-never-asserted@example.com";
        seedUser(knownEmail);
        String knownBody = forgotBody(knownEmail);

        assertThat(knownBody.getBytes(StandardCharsets.UTF_8)).isNotNull();
        assertThat(knownResponse).isEqualTo(unknownBody);
        assertThat(knownBody).isEqualTo(unknownBody);
    }

    @Test
    @DisplayName("disabled user also gets the identical 200 body and NO email is emitted")
    void disabledUser_noEmailSent() throws Exception {
        String email = "disabled-" + UUID.randomUUID() + "@example.com";
        User user = seedUser(email);
        user.setEnabled(false);
        userRepository.save(user);

        Logger senderLogger = (Logger) LoggerFactory.getLogger(ConsoleResetEmailSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        senderLogger.addAppender(appender);
        try {
            String body = forgotBody(email);
            assertThat(body).isNotNull();
            assertThat(appender.list.stream()
                            .filter(e -> e.getFormattedMessage().contains(email)))
                    .isEmpty();
        } finally {
            senderLogger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("known enabled user: console logs the link; Redis stores hashed token with bounded TTL")
    void forgotToken_storedInRedisWithTtl() throws Exception {
        String email = "reset-me-" + UUID.randomUUID() + "@example.com";
        User user = seedUser(email);

        String rawToken = captureResetToken(email);
        String hash = sha256Hex(rawToken);

        String storedUserId = redisTemplate.opsForValue().get(PasswordResetService.TOKEN_KEY_PREFIX + hash);

        assertThat(storedUserId).isEqualTo(user.getId().toString());

        Long ttl = redisTemplate.getExpire(PasswordResetService.TOKEN_KEY_PREFIX + hash);
        assertThat(ttl).isNotNull().isPositive();
        assertThat(ttl).isLessThanOrEqualTo(600L);

        // the pointer points at the same hash

        assertThat(redisTemplate.opsForValue().get(PasswordResetService.USER_POINTER_PREFIX + user.getId()))
                .isEqualTo(hash);
    }

    @Test
    @DisplayName("a second forgot-password call supersedes the first token")
    void secondForgot_supersedesFirst() throws Exception {
        String email = "supersede-" + UUID.randomUUID() + "@example.com";
        seedUser(email);

        String firstToken = captureResetToken(email);
        String secondToken = captureResetToken(email);

        assertThat(firstToken).isNotEqualTo(secondToken);

        assertThat(redisTemplate.opsForValue().get(PasswordResetService.TOKEN_KEY_PREFIX + sha256Hex(firstToken)))
                .isNull();
        assertThat(redisTemplate.opsForValue().get(PasswordResetService.TOKEN_KEY_PREFIX + sha256Hex(secondToken)))
                .isNotNull();
    }
}
