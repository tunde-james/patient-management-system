package com.devtunde.authservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.reset")
public record ResetProperties(String baseUrl, Duration tokenTtl) {}
