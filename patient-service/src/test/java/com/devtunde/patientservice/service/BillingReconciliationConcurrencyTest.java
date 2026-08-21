package com.devtunde.patientservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import billing.BillingResponse;
import com.devtunde.patientservice.grpc.BillingServiceGrpcClient;
import com.devtunde.patientservice.model.BillingProvisioningStatus;
import com.devtunde.patientservice.model.Patient;
import com.devtunde.patientservice.repository.PatientRepository;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {"billing.reconciliation.scheduler.enabled=false"})
class BillingReconciliationConcurrencyTest {

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
    @DisplayName("real flush/commit conflict: writer 2 (reconciler) stale-save yields cleanly, outer TX intact")
    void reconcile_staleSave_yieldsCleanlyOnRealOptimisticLock() {

        Patient seed = new Patient();
        seed.setName("Conflict Seed");
        seed.setEmail("conflict-" + UUID.randomUUID() + "@example.com");
        seed.setAddress("123 St");
        seed.setDateOfBirth(java.time.LocalDate.of(1990, 1, 1));
        seed.setRegisteredDate(java.time.LocalDate.of(2024, 1, 1));
        seed.setBillingStatus(BillingProvisioningStatus.PENDING);

        Patient saved = patientRepository.saveAndFlush(seed);
        UUID id = saved.getId();

        Patient writer1 = patientRepository.findById(id).orElseThrow();
        writer1.setBillingStatus(BillingProvisioningStatus.PROVISIONED);
        writer1.setBillingAccountId("WRITER1AAA");
        patientRepository.saveAndFlush(writer1);

        when(billingServiceGrpcClient.createBillingAccount(anyString(), anyString(), anyString()))
                .thenReturn(BillingResponse.newBuilder()
                        .setAccountId("WRITER2BBB")
                        .setStatus("ACTIVE")
                        .setCreated(true)
                        .build());

        Patient writer2 = patientRepository.findById(id).orElseThrow();

        ReflectionTestUtilsForTest.setVersion(writer2, 0L);

        boolean stillCandidate = reconciliationService.reconcile(writer2);

        assertThat(stillCandidate).isFalse();

        assertThat(writer2.getBillingStatus()).isEqualTo(BillingProvisioningStatus.PROVISIONED);

        Patient dbRow = patientRepository.findById(id).orElseThrow();

        assertThat(dbRow.getBillingAccountId()).isEqualTo("WRITER1AAA");

        assertThat(dbRow.getBillingStatus()).isEqualTo(BillingProvisioningStatus.PROVISIONED);
    }

    static final class ReflectionTestUtilsForTest {
        static void setVersion(Patient p, Long v) {
            ReflectionTestUtils.setField(p, "version", v);
        }
    }
}
