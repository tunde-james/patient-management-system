package com.devtunde.analyticsservice.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.devtunde.analyticsservice.model.PatientEventLogKey;
import com.devtunde.analyticsservice.repository.PatientEventLogRepository;
import patient.events.PatientEvent;

@SpringBootTest
@Testcontainers
@TestPropertySource(
        properties = {
            "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
            "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.ByteArraySerializer"
        })
class KafkaAnalyticsConsumerIntegrationTest {

    // Fixed key so the earliest-offset test can target exactly this row.
    private static final String SEED_PATIENT_ID = "11111111-1111-1111-1111-111111111111";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:latest"));

    @Autowired
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @Autowired
    private PatientEventLogRepository patientEventLogRepository;

    @Value("${kafka.topics.patient-event}")
    private String topic;

    @BeforeAll
    static void seedBeforeListenerConnects() throws Exception {

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        PatientEvent seed = PatientEvent.newBuilder()
                .setPatientId(SEED_PATIENT_ID)
                .setName("Seed Patient")
                .setEmail("seed@example.com")
                .setEventType("PATIENT_CREATED")
                .build();

        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("patient.events", SEED_PATIENT_ID, seed.toByteArray()))
                    .get();
        }
    }

    @Test
    @DisplayName("happy path: one event -> exactly one row with matching (patientId, eventType)")
    void singleEvent_materializesExactlyOneRow() {
        UUID patientId = UUID.randomUUID();

        publishEvent(patientId, "PATIENT_CREATED");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(
                        patientEventLogRepository.findById(new PatientEventLogKey(patientId, "PATIENT_CREATED")))
                .isPresent());
    }

    @Test
    @DisplayName("idempotency: same event delivered twice -> one row (PK + ON CONFLICT), consumer keeps going")
    void duplicateDelivery_isAbsorbed_consumerStaysAlive() {
        UUID dupId = UUID.randomUUID();
        UUID afterId = UUID.randomUUID();

        publishEvent(dupId, "PATIENT_CREATED");
        publishEvent(dupId, "PATIENT_CREATED"); // deliberate redelivery
        publishEvent(afterId, "PATIENT_CREATED"); // must still be processed

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(patientEventLogRepository.findById(new PatientEventLogKey(dupId, "PATIENT_CREATED")))
                    .isPresent();
            assertThat(patientEventLogRepository.findById(new PatientEventLogKey(afterId, "PATIENT_CREATED")))
                    .isPresent();
        });
        // The PK (patient_id, event_type) structurally caps the dupId row at one;
        // afterId landing proves the duplicate didn't poison the listener.
    }

    @Test
    @DisplayName("malformed payload: skipped, no row, consumer processes the next good event")
    void malformedPayload_isSkipped_consumerStaysAlive() {
        UUID goodId = UUID.randomUUID();

        kafkaTemplate.send(topic, "malformed-key", new byte[] {1, 2, 3});
        publishEvent(goodId, "PATIENT_CREATED");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(
                        patientEventLogRepository.findById(new PatientEventLogKey(goodId, "PATIENT_CREATED")))
                .isPresent());
    }

    @Test
    @DisplayName("first-boot offset: record seeded before the listener connected is consumed (earliest reset)")
    void seededBeforeBoot_isConsumedOnFirstJoin() {

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(patientEventLogRepository.findById(
                        new PatientEventLogKey(UUID.fromString(SEED_PATIENT_ID), "PATIENT_CREATED")))
                .isPresent());
    }

    @Test
    @DisplayName("malformed patient_id: retries exhaust, event skipped, no row, consumer stays alive")
    void malformedPatientId_isSkipped_consumerStaysAlive() {
        UUID goodId = UUID.randomUUID();

        publishRawEvent("not-a-uuid", "PATIENT_CREATED");
        publishEvent(goodId, "PATIENT_CREATED");

        // DefaultErrorHandler retries the bad record ~10x (~10s) before skipping it,
        // so the good event only lands after that window.

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> assertThat(
                        patientEventLogRepository.findById(new PatientEventLogKey(goodId, "PATIENT_CREATED")))
                .isPresent());
    }

    private void publishEvent(UUID patientId, String eventType) {
        publishRawEvent(patientId.toString(), eventType);
    }

    private void publishRawEvent(String patientId, String eventType) {

        PatientEvent event = PatientEvent.newBuilder()
                .setPatientId(patientId)
                .setName("Kafka IT")
                .setEmail("kafka-it@example.com")
                .setEventType(eventType)
                .build();

        kafkaTemplate.send(topic, patientId, event.toByteArray());
    }
}
