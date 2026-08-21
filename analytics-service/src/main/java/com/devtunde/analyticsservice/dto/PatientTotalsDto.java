package com.devtunde.analyticsservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PatientTotalsDto(
        @JsonProperty("total_enrolled") long totalEnrolled,
        @JsonProperty("total_billing_failed") long totalBillingFailed) {}
