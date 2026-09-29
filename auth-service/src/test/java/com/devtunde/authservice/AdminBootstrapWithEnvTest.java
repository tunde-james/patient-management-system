package com.devtunde.authservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.devtunde.authservice.model.User;
import com.devtunde.authservice.repository.UserRepository;
import com.devtunde.authservice.service.AdminBootstrapRunner;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test") 
@TestPropertySource(
        properties = {"auth.admin.email=admin-bootstrap@example.com", "auth.admin.password=BootstrapPass123"})
class AdminBootstrapWithEnvTest {

    private static final String ADMIN_EMAIL = "admin-bootstrap@example.com";
    private static final String ADMIN_PASSWORD = "BootstrapPass123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private AdminBootstrapRunner runner;

    @Autowired
    private UserRepository userRepository;

    private long adminCount() {
        return userRepository.findAll().stream()
                .filter(u -> "ROLE_ADMIN".equals(u.getRole()))
                .count();
    }

    @Test
    @DisplayName("startup with both env vars and no existing admin -> exactly one ROLE_ADMIN, hashed password")
    void startup_createsExactlyOneAdmin() {
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();

        assertThat(admin.getRole()).isEqualTo("ROLE_ADMIN");

        assertThat(admin.getPasswordHash()).doesNotContain(ADMIN_PASSWORD);
        assertThat(adminCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("second runner execution is a no-op (idempotent)")
    void rerun_isNoOp() {
        runner.run(null);

        assertThat(adminCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("creation emits AUDIT admin_bootstrapped with UUID only; never the plaintext password")
    void creation_emitsAuditWithoutPassword() {
        userRepository.deleteAll(); // force the creation path on the next run

        Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
        try {
            runner.run(null);

            assertThat(appender.list)
                    .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("event=admin_bootstrapped"));

            appender.list.forEach(
                    event -> assertThat(event.getFormattedMessage()).doesNotContain(ADMIN_PASSWORD));
        } finally {

            auditLogger.detachAppender(appender);
        }
    }
}
