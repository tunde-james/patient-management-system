package com.devtunde.patientservice.service;

import java.time.Duration;
import java.time.LocalDateTime;

import jakarta.persistence.OptimisticLockException;

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

import org.springframework.dao.OptimisticLockingFailureException;                                           
   import com.devtunde.patientservice.repository.PatientMutationGateway;

@Service
public class BillingReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(BillingReconciliationService.class);

    private final PatientRepository patientRepository;
    private final BillingServiceGrpcClient billingServiceGrpcClient;
    private final BillingReconciliationConfig config;
    private final PatientMutationGateway patientMutationGateway;

    public BillingReconciliationService(
            PatientRepository patientRepository,
            BillingServiceGrpcClient billingServiceGrpcClient,
            BillingReconciliationConfig config,
        PatientMutationGateway patientMutationGateway) {
        this.patientRepository = patientRepository;
        this.billingServiceGrpcClient = billingServiceGrpcClient;
        this.config = config;
        this.patientMutationGateway = patientMutationGateway;
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

            saveOrYield(patient);
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

            saveOrYield(patient);
        }
    }

    private void saveOrYield(Patient patient) {
        try {
            patientMutationGateway.save(patient);
        } catch (OptimisticLockingFailureException |OptimisticLockException ex) {
            log.warn(
                    "Reconciler: concurrent reconcile of patient {}; this attempt yields (other writer wins)",
                    patient.getId());
        }
    }

    LocalDateTime computeNextAttemptAt(Patient patient) {
        int attemptCount = patient.getBillingAttemptCount();
        if (attemptCount <= 0) {
            return LocalDateTime.now();
        }

        long baseMinutes = config.failedMinBackoff().toMinutes();
        long cappedMaxMinutes = config.failedMaxBackoff().toMinutes();
        long minutes = Math.min(baseMinutes * (1L << (attemptCount - 1)), cappedMaxMinutes);
        return patient.getBillingLastAttemptAt().plusMinutes(minutes);
    }
}
