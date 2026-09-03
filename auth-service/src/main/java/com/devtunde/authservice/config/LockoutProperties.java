package com.devtunde.authservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.security.lockout")
public record LockoutProperties(int maxAttempts, Duration lockDuration, Duration quietWindow) {}
