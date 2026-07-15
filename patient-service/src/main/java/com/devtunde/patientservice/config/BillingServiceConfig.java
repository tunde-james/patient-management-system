package com.devtunde.patientservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "billing.service")
public record BillingServiceConfig(
        @NotBlank String address,
        @NotNull @Positive int grpcPort,
        boolean usePlaintext,
        @NotNull @Positive int deadlineSeconds) {}
