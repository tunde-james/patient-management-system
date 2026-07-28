package com.devtunde.patientservice.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import billing.BillingResponse;
import com.devtunde.patientservice.config.BillingReconciliationConfig;
import com.devtunde.patientservice.exception.BillingProvisioningException;
import com.devtunde.patientservice.grpc.BillingServiceGrpcClient;
import com.devtunde.patientservice.model.BillingProvisioningStatus;
import com.devtunde.patientservice.model.Patient;
import com.devtunde.patientservice.repository.PatientRepository;

@Service
public class BillingReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(BillingReconciliationService.class);

    static final int FAILED_MAX_ATTEMPTS = 5;
    static final Duration FAILED_MIN_BACKOFF = Duration.ofMinutes(1);
    static final Duration FAILED_MAX_BACKOFF = Duration.ofMinutes(30);

    private final PatientRepository patientRepository;
    private final BillingServiceGrpcClient billingServiceGrpcClient;
    private final BillingReconciliationConfig config;

    public BillingReconciliationService(
            PatientRepository patientRepository,
            BillingServiceGrpcClient billingServiceGrpcClient,
            BillingReconciliationConfig config) {
        this.patientRepository = patientRepository;
        this.billingServiceGrpcClient = billingServiceGrpcClient;
        this.config = config;
    }

    @Transactional
    public boolean reconcile(Patient patient) {

        if (patient.getBillingStatus() == BillingProvisioningStatus.PROVISIONED) {
            return false;
        }

        if (patient.getBillingStatus() == BillingProvisioningStatus.FAILED) {
            if (patient.getBillingAttemptCount() >= config.failedMaxAttempts()) {
                log.error(
                        "Reconciler: patient {} exceeded the failed-attempt budget ({}); "
                                + "permanently FAILED until manual reset",
                        patient.getId(),
                        config.failedMaxAttempts());
                return false;
            }

            LocalDateTime nextAttemptAt = computeNextAttemptAt(patient);
            if (LocalDateTime.now().isBefore(nextAttemptAt)) {
                return true;
            }
        }

        attemptProvisioning(patient);

        return patient.getBillingStatus() != BillingProvisioningStatus.PROVISIONED;
    }

    private void attemptProvisioning(Patient patient) {
        try {
            BillingResponse response = billingServiceGrpcClient.createBillingAccount(
                    patient.getId().toString(), patient.getName(), patient.getEmail());

            patient.setBillingAccountId(response.getAccountId());
            patient.setBillingStatus(BillingProvisioningStatus.PROVISIONED);

            patient.setBillingAttemptCount(0);
            patient.setBillingLastAttemptAt(null);

            log.info("Reconciler: provisioned billing for patient {} (attempts to success: cleared)", patient.getId());

            patientRepository.save(patient);
        } catch (BillingProvisioningException ex) {
            patient.setBillingAccountId(null);
            patient.setBillingStatus(BillingProvisioningStatus.FAILED);
            patient.setBillingAttemptCount(patient.getBillingAttemptCount() + 1);
            patient.setBillingLastAttemptAt(LocalDateTime.now());

            log.warn(
                    "Reconciler: billing provisioning failed for patient {} " + "(attempt {}/{}, gRPC status={})",
                    patient.getId(),
                    patient.getBillingAttemptCount(),
                    config.failedMaxAttempts(),
                    ex.getStatusCode());

            patientRepository.save(patient);
        }
    }

     LocalDateTime computeNextAttemptAt(Patient patient) {
        int attemptCount = patient.getBillingAttemptCount();
        if (attemptCount <= 0) {
            return LocalDateTime.now();
        }

        long baseMinutes = config.failedMinBackoff().toMinutes();
        long cappedMaxMinutes = config.failedMaxBackoff().toMinutes();
        long minutes = Math.min(baseMinutes *(1L << (attemptCount - 1)), cappedMaxMinutes);
        return patient.getBillingLastAttemptAt().plusMinutes(minutes);
    }
}
