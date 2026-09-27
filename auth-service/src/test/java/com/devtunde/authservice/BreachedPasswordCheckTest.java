package com.devtunde.authservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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

import org.junit.jupiter.api.AfterAll;
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
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class BreachedPasswordCheckTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String FORGOT = "/api/v1/auth/forgot-password";
    private static final String RESET = "/api/v1/auth/reset-password";
    private static final Pattern LINK_TOKEN = Pattern.compile("link=\\S*token=([0-9a-f]{64})\\b");

    private static final String BREACHED_REGISTER_PASSWORD = "breached-register-pass-1";
    private static final String BREACHED_RESET_PASSWORD = "breached-reset-pass-1";

    private static final MockWebServer hibp = new MockWebServer();

    static {
        try {
            hibp.start();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Container
    static RedisContainer redis = new RedisContainer("redis:7");

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> redis.getRedisHost());
        registry.add("spring.data.redis.port", () -> redis.getRedisPort());

        registry.add("auth.security.breached-password-check.base-url", () -> baseUrl());
    }

    @AfterAll
    static void stopHibp() throws IOException {
        hibp.close();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("register with a breached password -> 400 problem+json, no user created")
    void register_breachedPassword_rejected() throws Exception {
        String email = "breach-" + UUID.randomUUID() + "@example.com";
        hibp.enqueue(new MockResponse().setBody(suffixOf(BREACHED_REGISTER_PASSWORD) + ":5000\n"));

        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content("""
                                   {
                                       "email":"%s",
                                       "password":"%s"
                                   }
                                   """.formatted(
                                email, BREACHED_REGISTER_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This password has appeared in a data breach; choose another."));

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("register with a password at the threshold -> 201")
    void register_atThreshold_allowed() throws Exception {
        String email = "threshold-" + UUID.randomUUID() + "@example.com";
        hibp.enqueue(new MockResponse().setBody(suffixOf("at-threshold-pass-1") + ":1000\n"));

        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content("""
                                   {
                                       "email":"%s",
                                       "password":"%s"
                                   }
                                   """.formatted(
                                email, "at-threshold-pass-1")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HIBP server error -> fail open, register succeeds")
    void register_hibpUnavailable_failsOpen() throws Exception {
        String email = "failopen-" + UUID.randomUUID() + "@example.com";
        hibp.enqueue(new MockResponse().setResponseCode(500));

        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content("""
                                   {
                                       "email":"%s",
                                       "password":"%s"
                                   }
                                   """.formatted(
                                email, "clean-fail-open-pass")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("reset with a breached password -> 400 and the token is NOT consumed")
    void reset_breachedPassword_tokenSurvives() throws Exception {
        String email = "reset-breach-" + UUID.randomUUID() + "@example.com";
        User user = userRepository.save(new User(email, passwordEncoder.encode("pass-word-1234")));

        String rawToken = captureResetToken(email);
        hibp.enqueue(new MockResponse().setBody(suffixOf(BREACHED_RESET_PASSWORD) + ":5000\n"));

        mockMvc.perform(post(RESET).contentType(MediaType.APPLICATION_JSON).content("""
                                   {
                                       "token":"%s",
                                       "newPassword":"%s"
                                   }
                                """.formatted(
                                rawToken, BREACHED_RESET_PASSWORD)))
                .andExpect(status().isBadRequest());

        assertThat(redisTemplate.opsForValue().get(PasswordResetService.TOKEN_KEY_PREFIX + sha256Hex(rawToken)))
                .isEqualTo(user.getId().toString());

        hibp.enqueue(new MockResponse().setBody(""));

        mockMvc.perform(post(RESET).contentType(MediaType.APPLICATION_JSON).content("""
                                   {
                                       "token":"%s",
                                       "newPassword":"%s"
                                   }
                                """.formatted(
                                rawToken, "clean-new-pass-77")))
                .andExpect(status().isOk());
    }

    private static String baseUrl() {
        return hibp.url("/").toString().replaceAll("/$", "");
    }

    private static String suffixOf(String password) throws Exception {
        return sha1Hex(password).substring(5);
    }

    private static String sha1Hex(String password) throws Exception {
        return HexFormat.of()
                .withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-1").digest(password.getBytes(StandardCharsets.UTF_8)));
    }

    private static String sha256Hex(String value) throws Exception {
        return HexFormat.of()
                .withLowerCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String captureResetToken(String email) throws Exception {
        Logger senderLogger = (Logger) LoggerFactory.getLogger(ConsoleResetEmailSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        senderLogger.addAppender(appender);
        try {
            mockMvc.perform(post(FORGOT).contentType(MediaType.APPLICATION_JSON).content("""
                                       {
                                           "email":"%s"
                                       }
                                    """.formatted(email)))
                    .andExpect(status().isOk());

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
}
