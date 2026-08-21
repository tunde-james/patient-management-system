package com.devtunde.patientservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import billing.BillingResponse;
import com.devtunde.patientservice.grpc.BillingServiceGrpcClient;
import com.devtunde.patientservice.kafka.KafkaPatientProducer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = "billing.reconciliation.scheduler.enabled=false")
@SuppressWarnings({"rawtypes", "unchecked"})
class PatientControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private BillingServiceGrpcClient billingServiceGrpcClient;

    @MockitoBean
    private KafkaPatientProducer kafkaPatientProducer;

    @BeforeEach
    void stubBillingClientHappyPath() {
        // Both smokes POST a patient; without a stub the gRPC mock returns null
        // and create blows up before the row is written.
        Mockito.when(billingServiceGrpcClient.createBillingAccount(
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyString()))
                .thenReturn(BillingResponse.newBuilder()
                        .setAccountId("AAAAAAAAAA")
                        .setStatus("ACTIVE")
                        .setCreated(true)
                        .build());
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static Map<String, Object> createBody(String email) {
        return Map.of(
                "name", "Ada Okafor",
                "email", email,
                "address", "12 Marina Road, Lagos",
                "dateOfBirth", "1990-06-01",
                "registeredDate", "2024-01-01");
    }

    @Test
    @DisplayName("smoke: POST -> GET by id -> PUT persists -> PATCH dob persists (no clobber)")
    void createReadUpdate_roundTripThroughDb() {

        String email = "smoke-" + UUID.randomUUID() + "@example.com";

        // POST -> 201 + Location
        ResponseEntity<Map> created = restTemplate.postForEntity(
                "/api/v1/patients", new HttpEntity<>(createBody(email), jsonHeaders()), Map.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).asString().startsWith("/api/v1/patients/");
        String id = (String) created.getBody().get("id");

        // GET by id -> row actually persisted
        ResponseEntity<Map> read = restTemplate.getForEntity("/api/v1/patients/" + id, Map.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody().get("email")).isEqualTo(email);
        assertThat(read.getBody().get("billingStatus")).isEqualTo("PROVISIONED");

        // PUT -> rename persists
        Map<String, Object> update =
                Map.of("name", "Ada Updated", "email", email, "address", "99 New Street, Abuja");
        ResponseEntity<Map> updated = restTemplate.exchange(
                "/api/v1/patients/" + id, HttpMethod.PUT, new HttpEntity<>(update, jsonHeaders()), Map.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("name")).isEqualTo("Ada Updated");

        // PATCH dob -> persists too (mapper + JPA update path)
        ResponseEntity<Map> patched = restTemplate.exchange(
                "/api/v1/patients/" + id + "/date-of-birth",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("dateOfBirth", "1985-08-15"), jsonHeaders()),
                Map.class);

        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody().get("dateOfBirth")).isEqualTo("1985-08-15");

        // Final read: PUT'd name AND PATCH'd dob coexist — two mappers writing
        // the same row is exactly where regressions hide.
        ResponseEntity<Map> finalRead = restTemplate.getForEntity("/api/v1/patients/" + id, Map.class);
        assertThat(finalRead.getBody().get("name")).isEqualTo("Ada Updated");
        assertThat(finalRead.getBody().get("dateOfBirth")).isEqualTo("1985-08-15");
    }

    @Test
    @DisplayName("smoke: DELETE soft-deletes -> gone from list, GET by id 404s")
    void delete_softDeletes_rowsHideAtDbLevel() {

        String email = "del-" + UUID.randomUUID() + "@example.com";

        ResponseEntity<Map> created = restTemplate.postForEntity(
                "/api/v1/patients", new HttpEntity<>(createBody(email), jsonHeaders()), Map.class);
        assertThat(created.getStatusCode())
                .as("POST must create before we can delete; body=%s", created.getBody())
                .isEqualTo(HttpStatus.CREATED);
        String id = (String) created.getBody().get("id");

        ResponseEntity<Void> deleteResponse =
                restTemplate.exchange("/api/v1/patients/" + id, HttpMethod.DELETE, null, Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List> list = restTemplate.getForEntity("/api/v1/patients", List.class);
        assertThat(list.getBody().stream()
                        .anyMatch(p -> ((Map<?, ?>) p).get("id").equals(id)))
                .as("deleted patient should not appear in GET /api/v1/patients")
                .isFalse();

        // @SQLRestriction("is_deleted = false") hides soft-deleted rows from findById,
        // so a deleted patient's id must 404 exactly like a never-existing one.
        ResponseEntity<Map> getById = restTemplate.getForEntity("/api/v1/patients/" + id, Map.class);
        assertThat(getById.getStatusCode())
                .as("GET by id on a soft-deleted patient must return 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
