package com.devtunde.patientservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import billing.BillingResponse;
import com.devtunde.patientservice.exception.BillingProvisioningException;
import com.devtunde.patientservice.grpc.BillingServiceGrpcClient;
import com.devtunde.patientservice.model.BillingProvisioningStatus;
import com.devtunde.patientservice.model.Patient;
import com.devtunde.patientservice.repository.PatientRepository;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {"billing.reconciliation.scheduler.enabled=false"})
public class ReconcilerResumeAfterRestartTest {

    private static final String ACCOUNT_ID = "RESUME0001";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private BillingReconciliationService reconciliationService;

    @MockitoBean
    private BillingServiceGrpcClient billingServiceGrpcClient;

    @Test
    @DisplayName("Scenario A: persisted attemptCount=1, lastAttemptAt=30s ago "
            + "A backoff RESUMES, row is skipped (not yet due), DB unchanged")
    void failedRow_recentAttempt_isSkipped_resumesPersistedBackoff() {

        LocalDateTime thirtySecondsAgo = LocalDateTime.now().minusSeconds(30).truncatedTo(ChronoUnit.MICROS);
        Patient persisted = insertFailedPatient(1, thirtySecondsAgo);

        UUID id = persisted.getId();
        BillingProvisioningStatus statusBefore = persisted.getBillingStatus();
        int countBefore = persisted.getBillingAttemptCount();
        LocalDateTime lastBefore = persisted.getBillingLastAttemptAt();

        mockBillingFailure();

        boolean stillCandidate = reconciliationService.reconcile(persisted);

        Patient reloaded = patientRepository.findById(id).orElseThrow();

        assertThat(stillCandidate).isTrue();
        assertThat(reloaded.getBillingStatus()).isEqualTo(statusBefore);
        assertThat(reloaded.getBillingAttemptCount()).isEqualTo(countBefore);
        assertThat(reloaded.getBillingAccountId()).isNull();
        assertThat(reloaded.getBillingLastAttemptAt()).isEqualTo(lastBefore);

        verify(billingServiceGrpcClient, never()).createBillingAccount(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Scenario B: persisted attemptCount=3, lastAttemptAt=1h ago "
            + "? backoff RESUMES, elapsed so retries, heals + resets counters "
            + "in Postgres")
    void failedRow_oldAttempt_backoffElapsed_retries_heals_resetsCounters() {

        Patient persisted = insertFailedPatient(3, LocalDateTime.now().minusHours(1));

        mockBillingSuccess();

        UUID id = persisted.getId();
        boolean stillCandidate = reconciliationService.reconcile(persisted);

        Patient reloaded = patientRepository.findById(id).orElseThrow();

        assertThat(stillCandidate).isFalse();
        assertThat(reloaded.getBillingStatus()).isEqualTo(BillingProvisioningStatus.PROVISIONED);
        assertThat(reloaded.getBillingAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(reloaded.getBillingAttemptCount()).isZero();
        assertThat(reloaded.getBillingLastAttemptAt()).isNull();

        verify(billingServiceGrpcClient, times(1)).createBillingAccount(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Scenario C: persisted attemptCount=5 (at budget) "
            + "? alerts and does NOT retry; persisted row untouched "
            + "(a crashed run had already exhausted the per-row budget; the "
            + "resumed run must not silently re-retry past the cap)")
    void failedRow_atBudget_doesNotRetry_noMutation() {
        Patient persisted = insertFailedPatient(5, LocalDateTime.now().minusHours(2));

        UUID id = persisted.getId();
        BillingProvisioningStatus statusBefore = persisted.getBillingStatus();
        int countBefore = persisted.getBillingAttemptCount();

        boolean stillCandidate = reconciliationService.reconcile(persisted);

        Patient reloaded = patientRepository.findById(id).orElseThrow();

        assertThat(stillCandidate).isFalse();
        assertThat(reloaded.getBillingStatus()).isEqualTo(statusBefore);
        assertThat(reloaded.getBillingAttemptCount()).isEqualTo(countBefore);
        verify(billingServiceGrpcClient, never()).createBillingAccount(anyString(), anyString(), anyString());
    }

    private Patient insertFailedPatient(int attemptCount, LocalDateTime lastAttemptAt) {

        Patient p = new Patient();
        p.setName("Resume Test");
        p.setEmail("resume-" + UUID.randomUUID() + "@example.com");
        p.setAddress("12 Crash Road, Lagos");
        p.setDateOfBirth(java.time.LocalDate.of(1985, 1, 1));
        p.setRegisteredDate(java.time.LocalDate.of(2024, 1, 1));
        p.setBillingStatus(BillingProvisioningStatus.FAILED);
        p.setBillingAccountId(null);
        p.setBillingAttemptCount(attemptCount);
        p.setBillingLastAttemptAt(lastAttemptAt);

        return patientRepository.save(p);
    }

    private void mockBillingSuccess() {
        Mockito.when(billingServiceGrpcClient.createBillingAccount(anyString(), anyString(), anyString()))
                .thenReturn(BillingResponse.newBuilder()
                        .setAccountId(ACCOUNT_ID)
                        .setStatus("ACTIVE")
                        .setCreated(true)
                        .build());
    }

    private void mockBillingFailure() {
        Mockito.when(billingServiceGrpcClient.createBillingAccount(anyString(), anyString(), anyString()))
                .thenThrow(BillingProvisioningException.unavailable("billing unavailable (test)"));
    }
}
