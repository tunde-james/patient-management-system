package com.devtunde.analyticsservice.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class PatientEventLogKey implements Serializable {

    private UUID patientId;
    private String eventType;

    public PatientEventLogKey() {}

    public PatientEventLogKey(UUID patientId, String eventType) {
        this.patientId = patientId;
        this.eventType = eventType;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public void setPatientId(UUID patientId) {
        this.patientId = patientId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof PatientEventLogKey other)) return false;

        return Objects.equals(patientId, other.patientId) && Objects.equals(eventType, other.eventType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(patientId, eventType);
    }
}
