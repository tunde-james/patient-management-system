package com.devtunde.patientservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatientReqDto(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(
                regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                message = "Please provide a valid email address")
        String email,

        @NotBlank(message = "Address is required")
        @Size(min = 5, max = 255, message = "Address must be between 5 and 255 characters")
        String address,

        @NotBlank(message = "Date of birth is required")
        @Pattern(regexp = "^\\d{2}-\\d{2}-\\d{4}$", message = "Date must be in the format DD-MM-YYYY")
        String dateOfBirth,

        @NotBlank(message = "Registered date is required")
        @Pattern(regexp = "^\\d{2}-\\d{2}-\\d{4}$", message = "Date must be in the format DD-MM-YYYY")
        String registeredDate) {}
