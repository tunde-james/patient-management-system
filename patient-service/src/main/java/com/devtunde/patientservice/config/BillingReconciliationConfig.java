package com.devtunde.patientservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "billing.reconciliation")
public record BillingReconciliationConfig(
        Duration runInterval, int failedMaxAttempts, Duration failedMinBackoff, Duration failedMaxBackoff) {}
