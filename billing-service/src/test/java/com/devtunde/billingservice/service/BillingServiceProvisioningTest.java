package com.devtunde.billingservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.DisplayName;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.devtunde.billingservice.config.BillingProperties;
import com.devtunde.billingservice.config.TestcontainersJpaTestConfig;
import com.devtunde.billingservice.model.AccountStatus;
import com.devtunde.billingservice.model.BillingAccount;
import com.devtunde.billingservice.model.Currency;
import com.devtunde.billingservice.repository.BillingAccountRepository;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    TestcontainersJpaTestConfig.class,
    BillingService.class,
    SecureRandomAccountIdGenerator.class,
    BillingAccountSaver.class,
    BillingProperties.class,
    CommittedBillingAccountSeeder.class
})
class BillingServiceProvisioningTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    BillingService billingService;

    @Autowired
    BillingAccountRepository billingAccountRepository;

    @Autowired
    BillingAccountSaver billingAccountSaver;

    @Autowired
    BillingProperties billingProperties;

    @Autowired
    CommittedBillingAccountSeeder committedBillingAccountSeeder;

    @BeforeEach
    void clearCommittedRowsFromPreviousTests() {
        committedBillingAccountSeeder.clearAllCommitted();
    }

    @Test
    @DisplayName("fresh provision creates an account with all defaults and created=true") 
    void provisionsFreshAccountWithDefaultsAndCreatedTrue() {
        String patientId = "11111111-1111-1111-1111-111111111111";
        String name = "Ada Okafor";
        String email = "ada@example.com";

        BillingService.ProvisioningResult result = billingService.provision(patientId, name, email);

        assertThat(result.created()).isTrue();
        assertThat(result.account())
                .extracting(BillingAccount::getPatientId, BillingAccount::getName, BillingAccount::getEmail)
                .containsExactly(patientId, name, email);
        assertThat(result.account().getAccountId()).hasSize(10).matches("[0-9A-Za-z]{10}");
        assertThat(result.account().getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.account().getCreditLimit()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(result.account().getCurrency()).isEqualTo(Currency.NGN);
        assertThat(result.account().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.account().getBillingCycleStart())
                .isCloseTo(LocalDateTime.now(), within(2, ChronoUnit.MINUTES));

        assertThat(billingAccountRepository.count()).isEqualTo(1L);
        assertThat(billingAccountRepository.findByPatientId(patientId))
                .get()
                .extracting(BillingAccount::getId)
                .isEqualTo(result.account().getId());
    }

    @Test
    @DisplayName("provisioning an existing patient returns the same account with created=false and no new row") 
    void provisioningAnExistingPatientReturnsSameAccountWithCreatedFalseAndNoNewRow() {
        String patientId = "22222222-2222-2222-2222-222222222222";
        String name = "Chidi Eze";
        String email = "chidi@example.com";

        BillingService.ProvisioningResult first = billingService.provision(patientId, name, email);
        assertThat(first.created()).isTrue();
        String firstAccountId = first.account().getAccountId();
        assertThat(firstAccountId).hasSize(10);

        long countAfterFirst = billingAccountRepository.count();

        BillingService.ProvisioningResult second = billingService.provision(patientId, name, email);

        assertThat(second.created()).isFalse();
        assertThat(second.account().getAccountId()).isEqualTo(firstAccountId);
        assertThat(second.account().getId()).isEqualTo(first.account().getId());
        assertThat(billingAccountRepository.count()).isEqualTo(countAfterFirst);
    }

    @Test
    @DisplayName("on a generated-accountId collision, the id is regenerated once and provisioning still succeeds")
    void regeneratesAccountIdOnceWhenTheGeneratedIdCollidesAndStillProvisions() {

        String collidingId = "ZZZZZZZZZZ";
        String sacrificialPatientId = "33333333-3333-3333-3333-333333333333";
        BillingAccount sacrificial = new BillingAccount(
                collidingId,
                sacrificialPatientId,
                "Sacrificial Patient",
                "sacrificial@example.com",
                BigDecimal.ZERO,
                new BigDecimal("0.00"),
                Currency.NGN,
                AccountStatus.ACTIVE,
                LocalDateTime.now());
        committedBillingAccountSeeder.seedCommitted(sacrificial);

        String safeId = "AAAAAAAAAA";
        AccountIdGenerator collidingThenSafe = new AccountIdGenerator() {
            private int calls = 0;

            @Override
            public String generate() {
                return calls++ == 0 ? collidingId : safeId;
            }
        };

        BillingService collidingBillingService =
                new BillingService(billingAccountRepository, billingAccountSaver, collidingThenSafe, billingProperties);

        String newPatientId = "44444444-4444-4444-4444-444444444444";
        BillingService.ProvisioningResult result =
                collidingBillingService.provision(newPatientId, "Ada Okafor", "ada@example.com");

        assertThat(result.created()).isTrue();
        assertThat(result.account().getAccountId()).isEqualTo(safeId);
        assertThat(result.account().getPatientId()).isEqualTo(newPatientId);
        assertThat(billingAccountRepository.count()).isEqualTo(2L);
        assertThat(billingAccountRepository.findByPatientId(newPatientId))
                .get()
                .extracting(BillingAccount::getId)
                .isEqualTo(result.account().getId());
    }
}
