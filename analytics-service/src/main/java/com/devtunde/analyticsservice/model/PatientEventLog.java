package com.devtunde.analyticsservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "patient_event_log")
@IdClass(PatientEventLogKey.class)
public class PatientEventLog {

    @Id
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Id
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    protected PatientEventLog() {}

    public PatientEventLog(UUID patientId, String eventType) {
        this.patientId = patientId;
        this.eventType = eventType;
    }

    @PrePersist
    void onCreate() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }

    public UUID getPatientId() {
        return patientId;
    }

    public String getEventType() {
        return eventType;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }
}
