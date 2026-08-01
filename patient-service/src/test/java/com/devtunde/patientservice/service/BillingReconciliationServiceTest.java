package com.devtunde.patientservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.OptimisticLockException;

import org.springframework.test.util.ReflectionTestUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import billing.BillingResponse;
import com.devtunde.patientservice.config.BillingReconciliationConfig;
import com.devtunde.patientservice.exception.BillingProvisioningException;
import com.devtunde.patientservice.grpc.BillingServiceGrpcClient;
import com.devtunde.patientservice.model.BillingProvisioningStatus;
import com.devtunde.patientservice.model.Patient;
import com.devtunde.patientservice.repository.PatientMutationGateway;
import com.devtunde.patientservice.repository.PatientRepository;

class BillingReconciliationServiceTest {

    private PatientRepository patientRepository;
    private BillingServiceGrpcClient billingServiceGrpcClient;
    private PatientMutationGateway patientMutationGateway;
    private BillingReconciliationService service;

    private static final BillingReconciliationConfig CONFIG = new BillingReconciliationConfig(
            Duration.ofSeconds(60), // run-interval
            5, // failed-max-attempts
            Duration.ofMinutes(1), // failed-min-backoff
            Duration.ofMinutes(30));

    private static final String ACCOUNT_ID = "ABCD1234XY";

    @BeforeEach
    void setUp() {
        patientRepository = Mockito.mock(PatientRepository.class);
        billingServiceGrpcClient = Mockito.mock(BillingServiceGrpcClient.class);
        patientMutationGateway = Mockito.mock(PatientMutationGateway.class);
        service = new BillingReconciliationService(
                patientRepository, billingServiceGrpcClient, CONFIG, patientMutationGateway);
    }

    private Patient pendingPatient() {
        Patient p = new Patient();
        ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
        p.setName("Test Pending");
        p.setEmail("pending@example.com");
        p.setBillingStatus(BillingProvisioningStatus.PENDING);
        p.setBillingAccountId(null);
        p.setBillingAttemptCount(0);
        p.setBillingLastAttemptAt(null);

        return p;
    }

    private Patient failedPatient(int attempts, LocalDateTime lastAttemptAt) {
        Patient p = new Patient();
        ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
        p.setName("Test Failed");
        p.setEmail("failed@example.com");
        p.setBillingStatus(BillingProvisioningStatus.FAILED);
        p.setBillingAccountId(null);
        p.setBillingAttemptCount(attempts);
        p.setBillingLastAttemptAt(lastAttemptAt);

        return p;
    }

    private void mockBillingSuccess() {
        when(billingServiceGrpcClient.createBillingAccount(anyString(), anyString(), anyString()))
                .thenReturn(BillingResponse.newBuilder()
                        .setAccountId(ACCOUNT_ID)
                        .setStatus("ACTIVE")
                        .setCreated(true)
                        .build());
    }

    private void mockBillingFailure() {
        when(billingServiceGrpcClient.createBillingAccount(anyString(), anyString(), anyString()))
                .thenThrow(BillingProvisioningException.unavailable("billing unavailable (test)"));
    }

    @Nested
    @DisplayName("PENDING rows")
    class PendingRows {

        @Test
        @DisplayName("PENDING row is provisioned immediately on the first reconcile call (no backoff)")
        void pendingRow_isProvisionedOnNextReconcile() {
            Patient p = pendingPatient();
            mockBillingSuccess();

            boolean stillCandidate = service.reconcile(p);

            assertThat(stillCandidate).isFalse();
            assertThat(p.getBillingStatus()).isEqualTo(BillingProvisioningStatus.PROVISIONED);
            assertThat(p.getBillingAccountId()).isEqualTo(ACCOUNT_ID);
            assertThat(p.getBillingAttemptCount()).isZero();
            assertThat(p.getBillingLastAttemptAt()).isNull();

            verify(patientMutationGateway, times(1)).save(p);
        }

        @Test
        @DisplayName("PENDING row whose first attempt fails becomes FAILED with attemptCount=1 + lastAttemptAt set")
        void pendingRow_firstAttemptFails_becomesFailedWithAttempt1() {
            Patient p = pendingPatient();
            mockBillingFailure();

            boolean stillCandidate = service.reconcile(p);

            assertThat(stillCandidate).isTrue();
            assertThat(p.getBillingStatus()).isEqualTo(BillingProvisioningStatus.FAILED);
            assertThat(p.getBillingAccountId()).isNull();
            assertThat(p.getBillingAttemptCount()).isEqualTo(1);
            assertThat(p.getBillingLastAttemptAt()).isNotNull();

            verify(patientMutationGateway, times(1)).save(p);
        }
    }

    @Nested
    @DisplayName("FAILED rows")
    class FailedRows {

        @Test
        @DisplayName("FAILED row past the budget (attemptCount >= maxAttempts) does NOT retry and alerts")
        void failedRow_pastBudget_doesNotRetry() {
            Patient p = failedPatient(5, LocalDateTime.now().minusHours(1));
            mockBillingSuccess();

            boolean stillCandidate = service.reconcile(p);

            assertThat(stillCandidate).isFalse();
            verify(billingServiceGrpcClient, never()).createBillingAccount(anyString(), anyString(), anyString());
            verify(patientMutationGateway, never()).save(any());
        }

        @Test
        @DisplayName("FAILED row still inside its backoff window is skipped this round (still a candidate)")
        void failedRow_insideBackoffWindow_isSkipped() {

            Patient p = failedPatient(1, LocalDateTime.now().minusSeconds(30));
            mockBillingSuccess();

            boolean stillCandidate = service.reconcile(p);

            assertThat(stillCandidate).isTrue();
            assertThat(p.getBillingStatus()).isEqualTo(BillingProvisioningStatus.FAILED);
            assertThat(p.getBillingAttemptCount()).isEqualTo(1);

            verify(billingServiceGrpcClient, never()).createBillingAccount(anyString(), anyString(), anyString());
            verify(patientMutationGateway, never()).save(any());
        }

        @Test
        @DisplayName("FAILED row whose backoff has elapsed IS retried; on success, audit counters reset")
        void failedRow_backoffElapsed_isRetriedAndSucceeds() {

            Patient p = failedPatient(1, LocalDateTime.now().minusMinutes(5));
            mockBillingSuccess();

            boolean stillCandidate = service.reconcile(p);

            assertThat(stillCandidate).isFalse();
            assertThat(p.getBillingStatus()).isEqualTo(BillingProvisioningStatus.PROVISIONED);
            assertThat(p.getBillingAccountId()).isEqualTo(ACCOUNT_ID);
            assertThat(p.getBillingAttemptCount()).isZero();
            assertThat(p.getBillingLastAttemptAt()).isNull();

            verify(patientMutationGateway, times(1)).save(p);
        }

        @Test
        @DisplayName("FAILED row retried and fails again: attemptCount increments + lastAttemptAt updates")
        void failedRow_retried_andFailsAgain_incrementsAttemptCount() {

            Patient p = failedPatient(2, LocalDateTime.now().minusMinutes(5));
            mockBillingFailure();

            LocalDateTime before = p.getBillingLastAttemptAt();
            boolean stillCandidate = service.reconcile(p);

            assertThat(stillCandidate).isTrue();
            assertThat(p.getBillingStatus()).isEqualTo(BillingProvisioningStatus.FAILED);
            assertThat(p.getBillingAttemptCount()).isEqualTo(3);
            assertThat(p.getBillingLastAttemptAt()).isAfterOrEqualTo(before);

            verify(patientMutationGateway, times(1)).save(p);
        }
    }

    @Nested
    @DisplayName("Optimistic locking (concurrent poller + consumer)")
    class OptimisticLocking {

        @Test
        @DisplayName("success-path save throws OptimisticLockException: swallowed, NOT rethrown, no retry save")
        void successPath_saveThrowsOLE_isSwallowedAndNotRethrown() {
            Patient p = pendingPatient();
            mockBillingSuccess();
            doThrow(new OptimisticLockException("concurrent write (test)"))
                    .when(patientMutationGateway)
                    .save(p);

            boolean stillCandidate = service.reconcile(p);

            assertThat(stillCandidate).isFalse();
            assertThat(p.getBillingStatus()).isEqualTo(BillingProvisioningStatus.PROVISIONED);
            verify(patientMutationGateway, times(1)).save(p);
        }

        @Test
        @DisplayName("failure-path save throws OptimisticLockException: swallowed, NOT rethrown, no retry save")
        void failurePath_saveThrowsOLE_isSwallowedAndNotRethrown() {
            Patient p = pendingPatient();
            mockBillingFailure();
            doThrow(new OptimisticLockException("concurrent write (test)"))
                    .when(patientMutationGateway)
                    .save(p);

            boolean stillCandidate = service.reconcile(p);

            assertThat(stillCandidate).isTrue();
            assertThat(p.getBillingStatus()).isEqualTo(BillingProvisioningStatus.FAILED);
            assertThat(p.getBillingAttemptCount()).isEqualTo(1);

            verify(patientMutationGateway, times(1)).save(p);
        }

        @Test
        @DisplayName(
                "gRPC failure itself is unrelated to OLE: still caught as BillingProvisioningException, save still attempted")
        void grpcFailure_stillRecordedAsFailed_saveAttempted() {
            Patient p = pendingPatient();
            mockBillingFailure();

            boolean stillCandidate = service.reconcile(p);

            assertThat(stillCandidate).isTrue();
            assertThat(p.getBillingStatus()).isEqualTo(BillingProvisioningStatus.FAILED);

            verify(patientMutationGateway, times(1)).save(p);
        }
    }

    @Test
    @DisplayName("PROVISIONED row is short-circuited: no gRPC call, no save, returns not-a-candidate")
    void provisionedRow_isShortCircuited() {
        Patient p = pendingPatient();
        p.setBillingStatus(BillingProvisioningStatus.PROVISIONED);
        p.setBillingAccountId("EXISTING00");

        boolean stillCandidate = service.reconcile(p);

        assertThat(stillCandidate).isFalse();

        verify(billingServiceGrpcClient, never()).createBillingAccount(anyString(), anyString(), anyString());
        verify(patientRepository, never()).save(any());
    }

    @Nested
    @DisplayName("computeNextAttemptAt backoff schedule (ADR-0001: 1m, 2m, 4m, 8m, 16m, cap 30m)")
    class ComputeNextAttemptAtSchedule {

        private LocalDateTime last = LocalDateTime.now().minusHours(1);

        private LocalDateTime compute(int attempts) {
            Patient p = failedPatient(attempts, last);

            return service.computeNextAttemptAt(p);
        }

        @Test
        @DisplayName("attemptCount=0: allowed immediately (now or before now) ? never-failed rows skip backoff")
        void zeroAttempts_allowedImmediately() {

            LocalDateTime next = compute(0);
            assertThat(next).isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(1));
        }

        @Test
        @DisplayName("attemptCount=1: next attempt allowed 1 minute after last attempt (2^0 = 1)")
        void oneAttempt_nextInOneMinute() {
            assertThat(compute(1)).isEqualTo(last.plusMinutes(1));
        }

        @Test
        @DisplayName("attemptCount=2: next attempt allowed 2 minutes after last attempt (2^1 = 2)")
        void twoAttempts_nextInTwoMinutes() {
            assertThat(compute(2)).isEqualTo(last.plusMinutes(2));
        }

        @Test
        @DisplayName("attemptCount=3: next attempt allowed 4 minutes (2^2 = 4)")
        void threeAttempts_nextInFourMinutes() {
            assertThat(compute(3)).isEqualTo(last.plusMinutes(4));
        }

        @Test
        @DisplayName("attemptCount=4: next attempt allowed 8 minutes (2^3 = 8)")
        void fourAttempts_nextInEightMinutes() {
            assertThat(compute(4)).isEqualTo(last.plusMinutes(8));
        }

        @Test
        @DisplayName("attemptCount=5: next attempt allowed 16 minutes (2^4 = 16)")
        void fiveAttempts_nextInSixteenMinutes() {
            assertThat(compute(5)).isEqualTo(last.plusMinutes(16));
        }

        @Test
        @DisplayName("attemptCount=6: CAPPED at 30 minutes (2^5 = 32, but max-backoff=30)")
        void sixAttempts_cappedAtThirtyMinutes() {
            assertThat(compute(6)).isEqualTo(last.plusMinutes(30));
        }

        @Test
        @DisplayName("attemptCount=100: still 30 minutes (the cap holds forever, not just at the boundary)")
        void oneHundredAttempts_stillCappedAtThirtyMinutes() {
            assertThat(compute(100)).isEqualTo(last.plusMinutes(30));
        }
    }

    @Test
    @DisplayName("reconcile never rethrows BillingProvisioningException ? it's caught inside attemptProvisioning")
    void reconcile_neverRethrowsBillingProvisioningException() {
        Patient p = pendingPatient();
        mockBillingFailure();

        boolean stillCandidate = service.reconcile(p);

        assertThat(stillCandidate).isTrue();
        assertThat(p.getBillingStatus()).isEqualTo(BillingProvisioningStatus.FAILED);
    }
}
