package com.devtunde.authservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.devtunde.authservice.repository.UserRepository;
import com.devtunde.authservice.service.AdminBootstrapRunner;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"auth.admin.email=", "auth.admin.password="})
class AdminBootstrapWithoutEnvTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private AdminBootstrapRunner runner;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("unset/blank env vars -> no admin exists after startup, manual run is still a no-op")
    void noEnv_noAdminEver() {

        assertThat(userRepository.existsByRole("ROLE_ADMIN")).isFalse();

        runner.run(null);

        assertThat(userRepository.existsByRole("ROLE_ADMIN")).isFalse();
    }
}
