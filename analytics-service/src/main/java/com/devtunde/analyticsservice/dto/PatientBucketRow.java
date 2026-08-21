package com.devtunde.analyticsservice.dto;

import java.sql.Date;
import java.time.LocalDate;

public record PatientBucketRow(Date date, String eventType, long count) {
    public LocalDate localDate() {
        return date.toLocalDate();
    }
}
