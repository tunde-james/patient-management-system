package com.devtunde.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.security.breached-password-check")
public record BreachedPasswordProperties(boolean enabled, String baseUrl) {}
