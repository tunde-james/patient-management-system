package com.devtunde.patientservice.kafka;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devtunde.patientservice.kafka.event_type.PatientEventType;
import com.devtunde.patientservice.model.Patient;
import com.devtunde.patientservice.repository.PatientRepository;
import com.devtunde.patientservice.service.BillingReconciliationService;
import patient.events.PatientEvent;

@Component
public class BillingReconciliationConsumer {

    private static final Logger log = LoggerFactory.getLogger(BillingReconciliationConsumer.class);

    private final PatientRepository patientRepository;
    private final BillingReconciliationService reconciliationService;

    public BillingReconciliationConsumer(
            PatientRepository patientRepository, BillingReconciliationService reconciliationService) {
        this.patientRepository = patientRepository;
        this.reconciliationService = reconciliationService;
    }

    @KafkaListener(topics = "${kafka.topics.patient-event}", groupId = "patient-service-reconciler")
    public void onPatientEvent(byte[] eventBytes) {

        PatientEvent event;

        try {
            event = PatientEvent.parseFrom(eventBytes);
        } catch (Exception ex) {
            log.warn("Reconciler consumer: ignored un-parseable PatientEvent payload", ex);
            return;
        }

        if (!PatientEventType.PATIENT_CREATED_BILLING_FAILED.name().equals(event.getEventType())) {
            return;
        }

        UUID patientId;

        try {
            patientId = UUID.fromString(event.getPatientId());
        } catch (IllegalArgumentException ex) {
            log.warn("Reconciler consumer: ignored event with malformed patientId '{}'", event.getPatientId());
            return;
        }

        Patient patient = patientRepository.findById(patientId).orElse(null);

        if (patient == null) {
            log.warn(
                    "Reconciler consumer: ignored billing-failed event for patient: {}: "
                            + "not found or soft-deleted; no retryable action",
                    patientId);
            return;
        }

        log.info("Reconciler consumer: received billing-failed event for patient {}", patientId);

        reconciliationService.reconcile(patient);
    }
}
