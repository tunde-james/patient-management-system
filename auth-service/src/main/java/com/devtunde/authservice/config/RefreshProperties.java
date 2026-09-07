package com.devtunde.authservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.refresh")
public record RefreshProperties(Duration rollingTtl, Duration absoluteTtl) {}
