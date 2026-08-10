package com.devtunde.analyticsservice.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PatientBucketDto(
        LocalDate date,
        @JsonProperty("enrolled") long enrolled,
        @JsonProperty("billing_failed") long billingFailed) {}
