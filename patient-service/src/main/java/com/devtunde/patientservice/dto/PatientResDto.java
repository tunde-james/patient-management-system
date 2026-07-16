package com.devtunde.patientservice.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

public record PatientResDto(
        String id,
        String name,
        String email,
        String address,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate dateOfBirth,

        String billingAccountId,
        String billingStatus) {}
