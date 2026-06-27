package com.devtunde.patientservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PatientDobUpdateReqDto(
        @NotBlank(message = "Date of birth is required")
        @Pattern(regexp = "^\\d{2}-\\d{2}-\\d{4}$", message = "Date of birth must be in the format DD-MM-YYYY")
        String dateOfBirth) {}
