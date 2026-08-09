package com.devtunde.analyticsservice.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.InvalidProtocolBufferException;
import patient.events.PatientEvent;

@Service
public class KafkaAnalyticsConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaAnalyticsConsumer.class);

    @KafkaListener(topics = "${kafka.topics.patient-event}", groupId = "analytics-service")
    public void consumerEvent(byte[] eventBytes) {

        PatientEvent patientEvent;

        try {
            patientEvent = PatientEvent.parseFrom(eventBytes);
        } catch (InvalidProtocolBufferException ex) {
            log.error("Error deserializing event {}", ex.getMessage());
        }
    }
}
