package com.devtunde.analyticsservice.kafka;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.springframework.kafka.support.Acknowledgment;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devtunde.analyticsservice.repository.PatientEventLogRepository;
import patient.events.PatientEvent;

@ExtendWith(MockitoExtension.class)
class KafkaAnalyticsConsumerTest {

    @Mock
    private PatientEventLogRepository patientEventLogRepository;

    @InjectMocks
    private KafkaAnalyticsConsumer consumer;

    @Test
    @DisplayName("valid event -> insertIfAbsent persist && acknowledge()")
    void consumeEvent_validEvent_insertsAndAcks() {

        UUID patientId = UUID.randomUUID();
        byte[] eventBytes = PatientEvent.newBuilder()
                .setPatientId(patientId.toString())
                .setEventType("PATIENT_CREATED")
                .build()
                .toByteArray();

        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>("patient.events", 0, 0L, "key", eventBytes);

        Acknowledgment ack = mock(Acknowledgment.class);

        when(patientEventLogRepository.insertIfAbsent(eq(patientId), eq("PATIENT_CREATED")))
                .thenReturn(1);

        consumer.consumerEvent(record, ack);

        verify(patientEventLogRepository).insertIfAbsent(eq(patientId), eq("PATIENT_CREATED"));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("unparseable bytes -> swallow poison && still acknowledge()")
    void consumeEvent_unparseableBytes_swallowsAndAcks() {
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("patient.events", 0, 7L, "key", new byte[] {1, 2, 3});

        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.consumerEvent(record, ack);

        verify(patientEventLogRepository, never()).insertIfAbsent(any(), any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("duplicate redelivery -> insertIfAbsent returns 0 (idempotent) && still acknowledge()")
    void consumeEvent_duplicateInsertIdempotent_returnsZeroAndAcks() {
        UUID patientId = UUID.randomUUID();
        byte[] eventBytes = PatientEvent.newBuilder()
                .setPatientId(patientId.toString())
                .setEventType("PATIENT_CREATED")
                .build()
                .toByteArray();
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>("patient.events", 0, 0L, "key", eventBytes);
        Acknowledgment ack = mock(Acknowledgment.class);

        when(patientEventLogRepository.insertIfAbsent(eq(patientId), eq("PATIENT_CREATED")))
                .thenReturn(0);

        consumer.consumerEvent(record, ack);

        verify(patientEventLogRepository).insertIfAbsent(eq(patientId), eq("PATIENT_CREATED"));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("malformed patient_id -> throw IllegalArgumentException, persist nothing, NO acknowledge()")
    void consumeEvent_malformedPatientId_throwsAndDoesNotAck() {
        byte[] eventBytes = PatientEvent.newBuilder()
                .setPatientId("not-a-uuid")
                .setEventType("PATIENT_CREATED")
                .build()
                .toByteArray();
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>("patient.events", 0, 5L, "key", eventBytes);
        Acknowledgment ack = mock(Acknowledgment.class);

        assertThrows(IllegalArgumentException.class, () -> consumer.consumerEvent(record, ack));

        verify(patientEventLogRepository, never()).insertIfAbsent(any(), any());
        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("DB write failure (non-PK exception) -> propagate, NO acknowledge()")
    void consumeEvent_dbWriteFailure_propagatesAndDoesNotAck() {
        UUID patientId = UUID.randomUUID();
        byte[] eventBytes = PatientEvent.newBuilder()
                .setPatientId(patientId.toString())
                .setEventType("PATIENT_CREATED")
                .build()
                .toByteArray();

        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>("patient.events", 0, 0L, "key", eventBytes);
        Acknowledgment ack = mock(Acknowledgment.class);

        when(patientEventLogRepository.insertIfAbsent(eq(patientId), eq("PATIENT_CREATED")))
                .thenThrow(new RuntimeException("Postgres down"));

        assertThrows(RuntimeException.class, () -> consumer.consumerEvent(record, ack));

        verify(ack, never()).acknowledge();
    }
}
