package com.devtunde.patientservice.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import com.fasterxml.jackson.annotation.JsonFormat;

public record PatientDobUpdateReqDto(
        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate dateOfBirth) {}
