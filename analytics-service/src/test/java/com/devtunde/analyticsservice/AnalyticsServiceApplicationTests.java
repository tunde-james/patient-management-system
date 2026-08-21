package com.devtunde.analyticsservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boot proof for issue 0007: full Spring context loads against a Testcontainers
 * Postgres via @ServiceConnection (so SPRING_DATASOURCE_* resolves to the
 * container), Flyway applies V1__baseline.sql, Hibernate validate confirms the
 * PatientEventLog entity matches the migrated schema. No @MockitoBean needed
 * here because analytics-service has no scheduling bean or Kafka listener that
 * would block context load (the consumer connects lazily; Spring Kafka tolerates
 * a missing broker at boot).
 */
@SpringBootTest
@Testcontainers
class AnalyticsServiceApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres =
			new PostgreSQLContainer<>("postgres:latest");

	@Test
	void contextLoads() {
	}

}
