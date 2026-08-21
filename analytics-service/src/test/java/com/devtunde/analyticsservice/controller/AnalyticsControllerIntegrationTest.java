package com.devtunde.analyticsservice.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.devtunde.analyticsservice.dto.PatientBucketsDto;
import com.devtunde.analyticsservice.dto.PatientTotalsDto;
import com.devtunde.analyticsservice.eventtype.AnalyticsEventType;
import com.devtunde.analyticsservice.model.PatientEventLog;
import com.devtunde.analyticsservice.repository.PatientEventLogRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AnalyticsControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PatientEventLogRepository patientEventLogRepository;

    private static final String BASE_URL = "/api/v1/analytics";

    @BeforeEach
    void cleanDb() {
        patientEventLogRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("smoke: total counts round-trip through HTTP + DB")
    void totals_roundTripThroughHttpAndDb() {

        insertAt(
                UUID.randomUUID(),
                AnalyticsEventType.PATIENT_CREATED,
                LocalDate.of(2026, 8, 1).atStartOfDay());
        insertAt(
                UUID.randomUUID(),
                AnalyticsEventType.PATIENT_CREATED,
                LocalDate.of(2026, 8, 1).atStartOfDay());
        insertAt(
                UUID.randomUUID(),
                AnalyticsEventType.PATIENT_CREATED_BILLING_FAILED,
                LocalDate.of(2026, 8, 1).atStartOfDay());

        ResponseEntity<PatientTotalsDto> response =
                restTemplate.getForEntity(BASE_URL + "/patients/total", PatientTotalsDto.class);

        // TEMP DIAG
        System.out.println("DIAG totals status=" + response.getStatusCode() + " body=" + response.getBody());
        // END TEMP

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().totalEnrolled()).isEqualTo(2L);
        assertThat(response.getBody().totalBillingFailed()).isEqualTo(1L);
    }

    @Test
    @DisplayName("smoke: by-day default window (frozen clock) returns expected shape")
    void byDay_defaultWindowRoundTrips() {
        LocalDate yesterday = LocalDate.of(2026, 8, 9); // within the default 30-day window
        LocalDate outOfWindow = LocalDate.of(2026, 1, 1); // outside default window

        insertAt(UUID.randomUUID(), AnalyticsEventType.PATIENT_CREATED, yesterday.atStartOfDay());
        insertAt(UUID.randomUUID(), AnalyticsEventType.PATIENT_CREATED, outOfWindow.atStartOfDay());

        ResponseEntity<PatientBucketsDto> response =
                restTemplate.getForEntity(BASE_URL + "/patients/by-day", PatientBucketsDto.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        PatientBucketsDto body = response.getBody();

        assertThat(body.from()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(body.to()).isEqualTo(LocalDate.of(2026, 8, 10));
        // Only the in-window event should appear — sparse, single bucket
        assertThat(body.buckets()).hasSize(1);
        assertThat(body.buckets().get(0).date()).isEqualTo(yesterday);
        assertThat(body.buckets().get(0).enrolled()).isEqualTo(1L);
    }

    private void insertAt(UUID patientId, AnalyticsEventType type, LocalDateTime receivedAt) {
        PatientEventLog log = new PatientEventLog(patientId, type.name());
        log.setReceivedAt(receivedAt);
        patientEventLogRepository.save(log);
    }
}
