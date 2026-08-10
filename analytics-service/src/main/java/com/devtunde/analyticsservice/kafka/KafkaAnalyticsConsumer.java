package com.devtunde.analyticsservice.kafka;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devtunde.analyticsservice.repository.PatientEventLogRepository;
import com.google.protobuf.InvalidProtocolBufferException;
import patient.events.PatientEvent;

@Service
public class KafkaAnalyticsConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaAnalyticsConsumer.class);

    private final PatientEventLogRepository patientEventLogRepository;

    public KafkaAnalyticsConsumer(PatientEventLogRepository patientEventLogRepository) {
        this.patientEventLogRepository = patientEventLogRepository;
    }

    @KafkaListener(topics = "${kafka.topics.patient-event}")
    public void consumerEvent(ConsumerRecord<String, byte[]> record, Acknowledgment acknowledgement) {

        PatientEvent event;

        try {
            event = PatientEvent.parseFrom(record.value());
        } catch (InvalidProtocolBufferException ex) {
            log.error(
                    "analytics: unparseable event at offset {} partition {}; committing and skipping",
                    record.offset(),
                    record.partition(),
                    ex.getMessage());
            acknowledgement.acknowledge();

            return;
        }

        try {
            int inserted = patientEventLogRepository.insertIfAbsent(
                    UUID.fromString(event.getPatientId()), event.getEventType());

            if (inserted == 1) {
                log.info("analytics: recorded {} for patient {}", event.getEventType(), event.getPatientId());
            } else {
                log.debug(
                        "analytics: duplicate {} for patient {} - idempotent no-op",
                        event.getEventType(),
                        event.getPatientId());
            }

            acknowledgement.acknowledge();
        } catch (IllegalArgumentException badUuid) {
            log.error(
                    "analytics: malformed patient_id '{}' at offset {}; not committing",
                    event.getPatientId(),
                    record.offset());
            throw badUuid;
        }
    }
}
