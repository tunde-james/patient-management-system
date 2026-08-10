package com.devtunde.analyticsservice.dto;

import java.time.LocalDate;
import java.util.List;

public record PatientBucketsDto(LocalDate from, LocalDate to, List<PatientBucketDto> buckets) {}
