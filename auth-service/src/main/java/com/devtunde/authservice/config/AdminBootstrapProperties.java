package com.devtunde.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.admin")
public record AdminBootstrapProperties(String email, String password) {}
