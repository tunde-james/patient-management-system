package com.devtunde.authservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.devtunde.authservice.service.ResetEmailSender;
import com.devtunde.authservice.service.SmtpResetEmailSender;
import com.redis.testcontainers.RedisContainer;

@Testcontainers
@SpringBootTest(properties = "spring.mail.host=localhost")
@ActiveProfiles("prod")
class ResetEmailSenderProfileTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Container
    static RedisContainer redis = new RedisContainer("redis:7");

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> redis.getRedisHost());
        registry.add("spring.data.redis.port", () -> redis.getRedisPort());
        registry.add("auth.security.breached-password-check.base-url", () -> "http://localhost:1");
    }

    @Autowired
    private ResetEmailSender resetEmailSender;

    @Test
    @DisplayName("prod profile selects the SMTP sender (console sender absent)")
    void prodProfile_usesSmtpSender() {

        assertThat(resetEmailSender).isInstanceOf(SmtpResetEmailSender.class);
    }
}
