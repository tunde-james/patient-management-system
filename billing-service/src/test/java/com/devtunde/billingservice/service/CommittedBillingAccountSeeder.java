package com.devtunde.billingservice.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.billingservice.model.BillingAccount;
import com.devtunde.billingservice.repository.BillingAccountRepository;

@Component
public class CommittedBillingAccountSeeder {

    private final BillingAccountRepository billingAccountRepository;

    public CommittedBillingAccountSeeder(BillingAccountRepository billingAccountRepository) {
        this.billingAccountRepository = billingAccountRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BillingAccount seedCommitted(BillingAccount account) {
        return billingAccountRepository.saveAndFlush(account);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearAllCommitted() {
        billingAccountRepository.deleteAllInBatch();
    }
}
