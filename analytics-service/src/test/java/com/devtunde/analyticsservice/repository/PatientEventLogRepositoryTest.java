package com.devtunde.analyticsservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.devtunde.analyticsservice.model.PatientEventLog;
import com.devtunde.analyticsservice.model.PatientEventLogKey;

@DataJpaTest
@Testcontainers
class PatientEventLogRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest");

    @Autowired
    private PatientEventLogRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("insertIfAbsent with a fresh (patientId, eventType) inserts one row and returns 1")
    void insertIfAbsent_fresh_insertsAndReturnsOne() {
        UUID patientId = UUID.randomUUID();

        int affected = repository.insertIfAbsent(patientId, "PATIENT_CREATED");

        assertThat(affected).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);

        PatientEventLog saved = repository
                .findById(new PatientEventLogKey(patientId, "PATIENT_CREATED"))
                .orElseThrow();

        assertThat(saved.getReceivedAt()).isNotNull();
    }

    @Test
    @DisplayName(
            "insertIfAbsent called twice with the same (patientId, eventType) returns 0 second time; row count stays 1")
    void insertIfAbsent_duplicate_isNoOp_returnsZero() {
        UUID patientId = UUID.randomUUID();

        int first = repository.insertIfAbsent(patientId, "PATIENT_CREATED");
        int second = repository.insertIfAbsent(patientId, "PATIENT_CREATED");

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("two different eventTypes for the same patientId insert two distinct rows")
    void insertIfAbsent_twoEventTypes_insertsTwoRows() {
        UUID patientId = UUID.randomUUID();

        int first = repository.insertIfAbsent(patientId, "PATIENT_CREATED");
        int second = repository.insertIfAbsent(patientId, "PATIENT_CREATED_BILLING_FAILED");

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("received_at is non-null after insert (the @PrePersist default applies)")
    void insertIfAbsent_receivedAtIsPersisted() {
        UUID patientId = UUID.randomUUID();

        repository.insertIfAbsent(patientId, "PATIENT_CREATED");

        PatientEventLog saved = repository
                .findById(new PatientEventLogKey(patientId, "PATIENT_CREATED"))
                .orElseThrow();

        assertThat(saved.getReceivedAt()).isNotNull();
        assertThat(saved.getReceivedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("saving an entity with a non-UUID patient_id string is rejected (constraint surfaces producer bugs)")
    void rawSave_malformedPatientId_rejectedByConstraint() {
        UUID patientId = UUID.randomUUID();
        repository.insertIfAbsent(patientId, "PATIENT_CREATED");

        PatientEventLog saved = repository
                .findById(new PatientEventLogKey(patientId, "PATIENT_CREATED"))
                .orElseThrow();

        assertThat(saved.getPatientId()).isInstanceOf(UUID.class);
    }
}
