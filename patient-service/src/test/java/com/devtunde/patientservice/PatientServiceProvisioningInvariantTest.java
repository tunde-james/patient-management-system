package com.devtunde.patientservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import billing.BillingResponse;
import com.devtunde.patientservice.dto.PatientReqDto;
import com.devtunde.patientservice.dto.PatientResDto;
import com.devtunde.patientservice.exception.BillingProvisioningException;
import com.devtunde.patientservice.grpc.BillingServiceGrpcClient;
import com.devtunde.patientservice.kafka.KafkaPatientProducer;
import com.devtunde.patientservice.model.BillingProvisioningStatus;
import com.devtunde.patientservice.model.Patient;
import com.devtunde.patientservice.repository.PatientRepository;
import com.devtunde.patientservice.scheduler.BillingReconciliationScheduler;
import com.devtunde.patientservice.service.PatientService;

@SpringBootTest
@Testcontainers
public class PatientServiceProvisioningInvariantTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest");

    @Autowired
    private PatientService patientService;

    @Autowired
    private PatientRepository patientRepository;

    @MockitoBean
    private BillingServiceGrpcClient billingServiceGrpcClient;

    @MockitoBean
    private BillingReconciliationScheduler billingReconciliationScheduler;

    @MockitoBean
    private KafkaPatientProducer kafkaPatientProducer;

    private static PatientReqDto aCreateRequest(String email) {
        return new PatientReqDto(
                "Ada Okafor",
                email,
                "12 Marina Road, Lagos",
                java.time.LocalDate.of(1990, 6, 1),
                java.time.LocalDate.of(2024, 1, 1));
    }

    @Test
    @DisplayName("FAILED path: persisted row has billingStatus=FAILED and billingAccountId=null (FAILED => null)")
    void createPatient_billingFailure_persistedRowHoldsFailedInvariant() {
        Mockito.when(billingServiceGrpcClient.createBillingAccount(
                        Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenThrow(BillingProvisioningException.unavailable("billing unavailable (test)"));

        String email = "inv-failed-" + UUID.randomUUID() + "@example.com";

        PatientResDto dto = patientService.createPatient(aCreateRequest(email));

        assertThat(dto.billingStatus()).isEqualTo("FAILED");
        assertThat(dto.billingAccountId()).isNull();

        Patient row = patientRepository.findById(UUID.fromString(dto.id())).orElseThrow();
        assertThat(row.getBillingStatus()).isEqualTo(BillingProvisioningStatus.FAILED);
        assertThat(row.getBillingAccountId()).isNull();
    }

    @Test
    @DisplayName(
            "PROVISIONED path: persisted row has billingStatus=PROVISIONED and billingAccountId!=null (=> non-null)")
    void createPatient_billingSuccess_persistedRowHoldsProvisionedInvariant() {

        String accountId = "CCCCCCCCCC";
        Mockito.when(billingServiceGrpcClient.createBillingAccount(
                        Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(BillingResponse.newBuilder()
                        .setAccountId(accountId)
                        .setStatus("ACTIVE")
                        .setCreated(true)
                        .build());

        String email = "inv-prov-" + UUID.randomUUID() + "@example.com";

        PatientResDto dto = patientService.createPatient(aCreateRequest(email));

        assertThat(dto.billingStatus()).isEqualTo("PROVISIONED");
        assertThat(dto.billingAccountId()).isEqualTo(accountId);

        Patient row = patientRepository.findById(UUID.fromString(dto.id())).orElseThrow();
        assertThat(row.getBillingStatus()).isEqualTo(BillingProvisioningStatus.PROVISIONED);
        assertThat(row.getBillingAccountId()).isEqualTo(accountId);
    }
}
