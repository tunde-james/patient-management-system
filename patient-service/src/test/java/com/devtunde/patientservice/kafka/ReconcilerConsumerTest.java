package com.devtunde.patientservice.kafka;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.devtunde.patientservice.exception.BillingProvisioningException;
import com.devtunde.patientservice.grpc.BillingServiceGrpcClient;
import com.devtunde.patientservice.kafka.event_type.PatientEventType;
import com.devtunde.patientservice.model.BillingProvisioningStatus;
import com.devtunde.patientservice.model.Patient;
import com.devtunde.patientservice.repository.PatientRepository;
import patient.events.PatientEvent;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "spring.task.scheduling.enabled=false",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
class ReconcilerConsumerTest {

    static final String TOPIC = "patient.events";
    static final String ACCOUNT_ID = "KAFKA0001";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Container
    @ServiceConnection
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka:latest"));

    @Autowired
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @Autowired
    private PatientRepository patientRepository;

    @MockitoBean
    private BillingServiceGrpcClient billingServiceGrpcClient;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(billingServiceGrpcClient);
        Mockito.when(billingServiceGrpcClient.createBillingAccount(anyString(), anyString(), anyString()))
                .thenThrow(BillingProvisioningException.unavailable("billing still down (consumer-test default)"));
    }

    @Test
    @DisplayName("PATIENT_CREATED_BILLING_FAILED event -> consumer receives, "
            + "parses the proto, findById-s the patient, and runs reconcile "
            + "(gRPC createBillingAccount fires on the shared service)")
    void billingFailedEvent_isConsumed_andTriggersReconcile() {

        Patient patient = insertFailedPatient();

        publishEvent(patient, PatientEventType.PATIENT_CREATED_BILLING_FAILED.name());

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                verify(billingServiceGrpcClient, atLeastOnce())
                        .createBillingAccount(patient.getId().toString(), patient.getName(), patient.getEmail()));
    }

    @Test
    @DisplayName("PATIENT_CREATED (success) event -> consumer's event_type filter "
            + "drops it; reconcile does NOT run, billing client NEVER called. "
            + "Mirrors the live Part-2 observation: billing-UP POST published a "
            + "PATIENT_CREATED event that the consumer silently ignored.")
    void patientCreatedSuccessEvent_isFilteredOut_reconcileDoesNotRun() {

        Patient patient = insertFailedPatient();

        publishEvent(patient, PatientEventType.PATIENT_CREATED.name());

        await().pollDelay(5, TimeUnit.SECONDS)
                .atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        verify(billingServiceGrpcClient, never())
                                .createBillingAccount(anyString(), anyString(), anyString()));
    }

    @Test
    @DisplayName("Un-parseable payload -> consumer swallows the "
            + "InvalidProtocolBufferException, no crash, no reconcile, "
            + "no gRPC call (at-least-once delivery must not poison the listener)")
    void malformedPayload_isSwallowed_listenerStaysAlive() {

        UUID fakeId = UUID.randomUUID();
        
        insertFailedPatient();

        kafkaTemplate.send(TOPIC, fakeId.toString(), "this-is-not-a-protobuf".getBytes());

        await().pollDelay(5, TimeUnit.SECONDS)
                .atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        verify(billingServiceGrpcClient, never())
                                .createBillingAccount(anyString(), anyString(), anyString()));
    }

    private Patient insertFailedPatient() {
        UUID id = UUID.randomUUID();
        Patient p = new Patient();
        p.setName("Reconciler Consumer Test");
        p.setEmail("kafka-" + id + "@example.com");
        p.setAddress("12 Kafka Mailroom Rd");
        p.setDateOfBirth(LocalDate.of(1992, 3, 11));
        p.setRegisteredDate(LocalDate.of(2026, 7, 27));
        p.setBillingStatus(BillingProvisioningStatus.FAILED);
        p.setBillingAccountId(null);
        p.setBillingAttemptCount(0);
        p.setBillingLastAttemptAt(null);
        
        p = patientRepository.save(p);
        return p;
    }

    private void publishEvent(Patient patient, String eventType) {
        PatientEvent event = PatientEvent.newBuilder()
                .setPatientId(patient.getId().toString())
                .setName(patient.getName())
                .setEmail(patient.getEmail())
                .setEventType(eventType)
                .build();
        kafkaTemplate.send(TOPIC, patient.getId().toString(), event.toByteArray());
    }
}
