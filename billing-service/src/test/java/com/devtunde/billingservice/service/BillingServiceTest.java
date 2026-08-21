package com.devtunde.billingservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.devtunde.billingservice.config.BillingProperties;
import com.devtunde.billingservice.model.BillingAccount;
import com.devtunde.billingservice.repository.BillingAccountRepository;

/**
 * Pure-Mockito branch-matrix of {@link BillingService#provision} (issue 0012).
 * The concurrent-winner branch was previously untestable against a real DB —
 * mocking stages the race deterministically.
 */
@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private BillingAccountRepository billingAccountRepository;

    @Mock
    private BillingAccountSaver billingAccountSaver;

    @Mock
    private AccountIdGenerator accountIdGenerator;

    @Mock
    private BillingProperties billingProperties;

    @InjectMocks
    private BillingService billingService;

    // Note: the constructor reads billingProperties.getDefaultCreditLimit() during
    // @InjectMocks instantiation — before any stub can exist — so it receives null
    // here. No test asserts the credit limit, so no stub is needed.

    private static BillingAccount account(String accountId) {
        return new BillingAccount(
                accountId,
                "patient-1",
                "Ada Okafor",
                "ada@example.com",
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.valueOf(500),
                com.devtunde.billingservice.model.Currency.NGN,
                com.devtunde.billingservice.model.AccountStatus.ACTIVE,
                java.time.LocalDateTime.now());
    }

    @Test
    @DisplayName("fresh provision -> generated id, saved, created=true")
    void provision_fresh_creates() {

        when(billingAccountRepository.findByPatientId("patient-1")).thenReturn(Optional.empty());
        when(accountIdGenerator.generate()).thenReturn("GEN-001");
        when(billingAccountSaver.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = billingService.provision("patient-1", "Ada Okafor", "ada@example.com");

        assertTrue(result.created());
        assertEquals("GEN-001", result.account().getAccountId());
        verify(billingAccountSaver).save(any());
    }

    @Test
    @DisplayName("existing patient -> returned as-is, created=false, no save")
    void provision_existing_idempotent() {

        BillingAccount existing = account("EXISTING-1");
        when(billingAccountRepository.findByPatientId("patient-1")).thenReturn(Optional.of(existing));

        var result = billingService.provision("patient-1", "Ada Okafor", "ada@example.com");

        assertFalse(result.created());
        assertEquals("EXISTING-1", result.account().getAccountId());
        verify(billingAccountSaver, never()).save(any());
    }

    @Test
    @DisplayName("collision + concurrent writer already won -> return winner, created=false")
    void provision_collisionConcurrentWinner_returnsWinner() {

        // First lookup: empty (so we try to create). Save hits the unique
        // constraint because a concurrent request inserted first. Re-lookup:
        // the winner is now there.
        BillingAccount winner = account("WINNER-1");
        when(billingAccountRepository.findByPatientId("patient-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(accountIdGenerator.generate()).thenReturn("LOSER-1");
        when(billingAccountSaver.save(any()))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));

        var result = billingService.provision("patient-1", "Ada Okafor", "ada@example.com");

        assertFalse(result.created());
        assertEquals("WINNER-1", result.account().getAccountId());
        verify(accountIdGenerator).generate();  // exactly once — no pointless regeneration
    }

    @Test
    @DisplayName("collision + still no winner (spurious constraint hit) -> regenerate once, created=true")
    void provision_collisionSpurious_regeneratesAndSucceeds() {

        when(billingAccountRepository.findByPatientId("patient-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(accountIdGenerator.generate()).thenReturn("FIRST-1", "SECOND-1");
        when(billingAccountSaver.save(any()))
                .thenThrow(new DataIntegrityViolationException("unique constraint"))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = billingService.provision("patient-1", "Ada Okafor", "ada@example.com");

        assertTrue(result.created());
        assertEquals("SECOND-1", result.account().getAccountId());
    }
}
