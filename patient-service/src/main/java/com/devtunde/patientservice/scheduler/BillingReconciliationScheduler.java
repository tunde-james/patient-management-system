package com.devtunde.patientservice.scheduler;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devtunde.patientservice.model.BillingProvisioningStatus;
import com.devtunde.patientservice.model.Patient;
import com.devtunde.patientservice.repository.PatientRepository;
import com.devtunde.patientservice.service.BillingReconciliationService;

@Component
@ConditionalOnProperty(
        prefix = "billing.reconciliation.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BillingReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingReconciliationScheduler.class);

    private final PatientRepository patientRepository;
    private final BillingReconciliationService reconciliationService;

    public BillingReconciliationScheduler(
            PatientRepository patientRepository, BillingReconciliationService reconciliationService) {
        this.patientRepository = patientRepository;
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(fixedDelayString = "${billing.reconciliation.run-interval:PT60S}")
    public void reconcilePendingAndFailedPatients() {

        List<Patient> candidates = patientRepository.findByBillingStatusIn(
                List.of(BillingProvisioningStatus.PENDING, BillingProvisioningStatus.FAILED));

        if (candidates.isEmpty()) {
            return;
        }

        log.info("Reconciler tick: {} candidate patient(s) to consider", candidates.size());

        for (Patient patient : candidates) {
            try {
                reconciliationService.reconcile(patient);
            } catch (Exception ex) {
                log.error("Reconciler: unexpected error processing patient {}", patient.getId(), ex);
            }
        }
    }
}
