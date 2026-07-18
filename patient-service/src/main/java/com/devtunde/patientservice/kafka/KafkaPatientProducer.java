package com.devtunde.patientservice.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devtunde.patientservice.model.Patient;
import patient.events.PatientEvent;

@Service
public class KafkaPatientProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaPatientProducer.class);

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final KafkaTopicsConfig topicsConfig;

    public KafkaPatientProducer(KafkaTemplate<String, byte[]> kafkaTemplate, KafkaTopicsConfig topicsConfig) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicsConfig = topicsConfig;
    }

    public void sendEvent(Patient patient, String eventType) {

        PatientEvent event = PatientEvent.newBuilder()
                .setPatientId(patient.getId().toString())
                .setName(patient.getName())
                .setEmail(patient.getEmail())
                .setEventType(eventType)
                .build();

        kafkaTemplate
                .send(topicsConfig.patientEvent(), patient.getId().toString(), event.toByteArray())
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info(
                                "Published PatientCreated event for patient {} to partition {} at offset {}",
                                patient.getId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("Failed to publish PatientCreated event for patient {}", patient.getId(), ex);
                    }
                });
    }
}
