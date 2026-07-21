package com.devtunde.patientservice.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "patient")
@SQLRestriction("is_deleted = false")
public class Patient extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    @NotBlank(message = "Name is required")
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    @NotBlank(message = "Email is required")
    @Email(
            regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
            message = "Please provide a valid email address")
    private String email;

    @Column(nullable = false)
    @NotBlank(message = "Address is required")
    private String address;

    @NotNull(message = "Date of birth is required")
    private LocalDate dateOfBirth;

    @NotNull(message = "Registered date is required")
    private LocalDate registeredDate;

    @Column(name = "billing_account_id", length = 10)
    private String billingAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_status", nullable = false, length = 12)
    private BillingProvisioningStatus billingStatus = BillingProvisioningStatus.PENDING;

    @Column(name = "billing_attempt_count", nullable = false, columnDefinition = "integer not null default 0")
    private int billingAttemptCount = 0;

    @Column(name = "billing_last_attempt_at")
    private LocalDateTime billingLastAttemptAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public LocalDate getRegisteredDate() {
        return registeredDate;
    }

    public void setRegisteredDate(LocalDate registeredDate) {
        this.registeredDate = registeredDate;
    }

    public String getBillingAccountId() {
        return billingAccountId;
    }

    public void setBillingAccountId(String billingAccountId) {
        this.billingAccountId = billingAccountId;
    }

    public BillingProvisioningStatus getBillingStatus() {
        return billingStatus;
    }

    public void setBillingStatus(BillingProvisioningStatus billingStatus) {
        this.billingStatus = billingStatus;
    }

    public int getBillingAttemptCount() {
        return billingAttemptCount;
    }

    public void setBillingAttemptCount(int billingAttemptCount) {
        this.billingAttemptCount = billingAttemptCount;
    }

    public LocalDateTime getBillingLastAttemptAt() {
        return billingLastAttemptAt;
    }

    public void setBillingLastAttemptAt(LocalDateTime billingLastAttemptAt) {
        this.billingLastAttemptAt = billingLastAttemptAt;
    }
}
