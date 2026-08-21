package com.devtunde.analyticsservice.kafka;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devtunde.analyticsservice.eventtype.AnalyticsEventType;
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
    @Transactional
    public void consumerEvent(ConsumerRecord<String, byte[]> record, Acknowledgment acknowledgement) {

        PatientEvent event;

        try {
            event = PatientEvent.parseFrom(record.value());
        } catch (InvalidProtocolBufferException ex) {
            log.error(
                    "analytics: unparseable event at offset {} partition {}", record.offset(), record.partition(), ex);
            acknowledgement.acknowledge();

            return;
        }

        AnalyticsEventType type;

        try {
            type = AnalyticsEventType.valueOf(event.getEventType());
        } catch (IllegalArgumentException unknown) {
            log.error(
                    "analytics: unsupported event type '{}' at offset {} partition {}; committing and skipping",
                    event.getEventType(),
                    record.offset(),
                    record.partition());

            acknowledgement.acknowledge();
            return;
        }

        try {
            int inserted = patientEventLogRepository.insertIfAbsent(UUID.fromString(event.getPatientId()), type.name());

            if (inserted == 1) {
                log.info(
                        "analytics: recorded event type {} at offset {} partition {}",
                        type,
                        record.offset(),
                        record.partition());
            } else {
                log.debug(
                        "analytics: duplicate event type {} at offset {} partition {} - idempotent no-op",
                        type,
                        record.offset(),
                        record.partition());
            }

            acknowledgement.acknowledge();
        } catch (IllegalArgumentException badUuid) {
            log.error(
                    "analytics: malformed patient_id at offset {} partition {}; not committing",
                    record.offset(),
                    record.partition());

            throw badUuid;
        }
    }
}
