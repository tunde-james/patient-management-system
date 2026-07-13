package com.devtunde.billingservice.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.devtunde.billingservice.config.BillingProperties;
import com.devtunde.billingservice.model.AccountStatus;
import com.devtunde.billingservice.model.BillingAccount;
import com.devtunde.billingservice.model.Currency;
import com.devtunde.billingservice.repository.BillingAccountRepository;

@Service
public class BillingService {

    public record ProvisioningResult(boolean created, BillingAccount account) {}

    private final BillingAccountRepository billingAccountRepository;
    private final BillingAccountSaver billingAccountSaver;
    private final AccountIdGenerator accountIdGenerator;
    private final BigDecimal defaultCreditLimit;

    public BillingService(
            BillingAccountRepository billingAccountRepository,
            BillingAccountSaver billingAccountSaver,
            AccountIdGenerator accountIdGenerator,
            BillingProperties billingProperties) {
        this.billingAccountRepository = billingAccountRepository;
        this.billingAccountSaver = billingAccountSaver;
        this.accountIdGenerator = accountIdGenerator;
        this.defaultCreditLimit = billingProperties.getDefaultCreditLimit();
    }

    public ProvisioningResult provision(String patientId, String name, String email) {

        Optional<BillingAccount> existing = billingAccountRepository.findByPatientId(patientId);

        if (existing.isPresent()) {
            return new ProvisioningResult(false, existing.get());
        }

        BillingAccount firstAttempt = buildNewAccount(patientId, name, email, accountIdGenerator.generate());

        try {
            BillingAccount saved = billingAccountSaver.save(firstAttempt);

            return new ProvisioningResult(true, saved);
        } catch (DataIntegrityViolationException e) {
            Optional<BillingAccount> existingAfterCollision = billingAccountRepository.findByPatientId(patientId);
            if (existingAfterCollision.isPresent()) {
                return new ProvisioningResult(false, existingAfterCollision.get());
            }
            
            BillingAccount retried = buildNewAccount(patientId, name, email, accountIdGenerator.generate());
            BillingAccount saved = billingAccountSaver.save(retried);

            return new ProvisioningResult(true, saved);
        }
    }

    private BillingAccount buildNewAccount(String patientId, String name, String email, String accountId) {
        return new BillingAccount(
                accountId,
                patientId,
                name,
                email,
                BigDecimal.ZERO,
                defaultCreditLimit,
                Currency.NGN,
                AccountStatus.ACTIVE,
                LocalDateTime.now());
    }
}
