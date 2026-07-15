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
import com.devtunde.patientservice.exception.BillingProvisioningException;
import com.devtunde.patientservice.grpc.BillingServiceGrpcClient;
import io.grpc.Status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@SuppressWarnings({"rawtypes", "unchecked"})
class PatientControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest");

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private BillingServiceGrpcClient billingServiceGrpcClient;

    @BeforeEach
    void stubBillingClientHappyPath() {
        stubBillingSuccess("AAAAAAAAAA", true);
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void overrideBillingSuccess(String accountId, boolean created) {
        Mockito.reset(billingServiceGrpcClient);
        stubBillingSuccess(accountId, created);
    }

    private void overrideBillingFailure(Status.Code code) {
        Mockito.reset(billingServiceGrpcClient);
        stubBillingFailure(code);
    }

    private void stubBillingSuccess(String accountId, boolean created) {
        Mockito.when(billingServiceGrpcClient.createBillingAccount(
                        ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(BillingResponse.newBuilder()
                        .setAccountId(accountId)
                        .setStatus("ACTIVE")
                        .setCreated(created)
                        .build());
    }

    private void stubBillingFailure(Status.Code code) {
        Mockito.when(billingServiceGrpcClient.createBillingAccount(
                        ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenThrow(new BillingProvisioningException(code, "billing failure (test)"));
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
    @DisplayName("POST /api/v1/patients creates a patient, provisions billing, and returns 201")
    void createPatient_returnsCreatedWithCorrectFields_andProvisionedBilling() {

        String email = "ada-" + UUID.randomUUID() + "@example.com";

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/patients", new HttpEntity<>(createBody(email), jsonHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("name")).isEqualTo("Ada Okafor");
        assertThat(body.get("email")).isEqualTo(email); // <-- catches the swap bug
        assertThat(body.get("address")).isEqualTo("12 Marina Road, Lagos");
        assertThat(body.get("dateOfBirth")).isEqualTo("1990-06-01");
        assertThat(body.get("billingAccountId")).isEqualTo("AAAAAAAAAA");
        assertThat(body.get("billingStatus")).isEqualTo("PROVISIONED");
        assertThat(body.get("id")).asString().isNotEmpty();
        assertThat(response.getHeaders().getLocation()).asString().startsWith("/api/v1/patient/");
    }

    @Test
    @DisplayName("POST /api/v1/patients on an idempotent billing hit still returns 201 with PROVISIONED")
    void createPatient_idempotentBillingHit_stillProvisioned() {

        overrideBillingSuccess("BBBBBBBBBB", false);

        String email = "idem-" + UUID.randomUUID() + "@example.com";

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/patients", new HttpEntity<>(createBody(email), jsonHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("billingAccountId")).isEqualTo("BBBBBBBBBB");
        assertThat(body.get("billingStatus")).isEqualTo("PROVISIONED");
    }

    @Test
    @DisplayName("POST /api/v1/patients when billing is UNAVAILABLE returns 201 with billingStatus=FAILED")
    void createPatient_billingUnavailable_returns201Failed() {

        overrideBillingFailure(Status.Code.UNAVAILABLE);

        String email = "down-" + UUID.randomUUID() + "@example.com";

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/patients", new HttpEntity<>(createBody(email), jsonHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("billingStatus")).isEqualTo("FAILED");
        assertThat(body.get("billingAccountId")).isNull();
        assertThat(body.get("id")).asString().isNotEmpty();
    }

    @Test
    @DisplayName("POST /api/v1/patients when billing exceeds the deadline returns 201 with billingStatus=FAILED")
    void createPatient_billingDeadlineExceeded_returns201Failed() {

        overrideBillingFailure(Status.Code.DEADLINE_EXCEEDED);

        String email = "slow-" + UUID.randomUUID() + "@example.com";

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/patients", new HttpEntity<>(createBody(email), jsonHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("billingStatus")).isEqualTo("FAILED");
        assertThat(body.get("billingAccountId")).isNull();
    }

    @Test
    @DisplayName("POST /api/v1/patients with a duplicate email returns 409")
    void createPatient_duplicateEmailReturns409() {

        String email = "dup-" + UUID.randomUUID() + "@example.com";

        ResponseEntity<Map> first = restTemplate.postForEntity(
                "/api/v1/patients", new HttpEntity<>(createBody(email), jsonHeaders()), Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> second = restTemplate.postForEntity(
                "/api/v1/patients", new HttpEntity<>(createBody(email), jsonHeaders()), Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().get("title")).asString().contains("Email");
    }

    @Test
    @DisplayName("POST /api/v1/patients with a bad date format returns 400 invalid-format")
    void createPatient_badDateFormatReturns400InvalidFormat() {

        String email = "bad-date-" + UUID.randomUUID() + "@example.com";

        Map<String, Object> bad = Map.of(
                "name", "Ada Okafor",
                "email", email,
                "address", "12 Marina Road, Lagos",
                "dateOfBirth", "01/06/1990", // wrong format, should be yyyy-MM-dd
                "registeredDate", "2024-01-01");

        ResponseEntity<Map> response =
                restTemplate.postForEntity("/api/v1/patients", new HttpEntity<>(bad, jsonHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("title")).asString().contains("Invalid format");
        assertThat(response.getBody().get("detail")).asString().contains("dateOfBirth");
    }

    @Test
    @DisplayName("POST /api/v1/patients with an empty name returns 400 with a validation problem+json body")
    void createPatient_invalidBodyReturns400() {

        Map<String, Object> bad = Map.of(
                "name", "",
                "email", "x@example.com",
                "address", "some address that is long enough",
                "dateOfBirth", "1990-01-01",
                "registeredDate", "2024-01-01");

        ResponseEntity<Map> response =
                restTemplate.postForEntity("/api/v1/patients", new HttpEntity<>(bad, jsonHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("title")).asString().contains("Validation");
    }

    @Test
    @DisplayName("GET /api/v1/patients returns the list including a newly created patient")
    void getPatients_returnsAllCreatedPatients() {

        String email = "list-" + UUID.randomUUID() + "@example.com";

        ResponseEntity<Map> created = restTemplate.postForEntity(
                "/api/v1/patients", new HttpEntity<>(createBody(email), jsonHeaders()), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<List> list = restTemplate.getForEntity("/api/v1/patients", List.class);

        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("PUT /api/v1/patients/{id} updates name/email/address and returns 200 with the updated patient")
    void updatePatient_updatesNameEmailAddress() {

        String email = "upd-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<Map> created = restTemplate.postForEntity(
                "/api/v1/patients", new HttpEntity<>(createBody(email), jsonHeaders()), Map.class);
        String id = (String) created.getBody().get("id");

        Map<String, Object> update =
                Map.of("name", "Ada Updated", "email", "updated-" + email, "address", "99 New Street, Abuja");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/patients/" + id, HttpMethod.PUT, new HttpEntity<>(update, jsonHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("name")).isEqualTo("Ada Updated");
        assertThat(body.get("email")).isEqualTo("updated-" + email);
        assertThat(body.get("address")).isEqualTo("99 New Street, Abuja");
    }

    @Test
    @DisplayName("PATCH /api/v1/patients/{id}/date-of-birth updates dob and returns 200")
    void updatePatientDob_updatesDobOnly() {

        String email = "dob-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<Map> created = restTemplate.postForEntity(
                "/api/v1/patients", new HttpEntity<>(createBody(email), jsonHeaders()), Map.class);
        String id = (String) created.getBody().get("id");

        Map<String, Object> patch = Map.of("dateOfBirth", "1985-08-15");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/patients/" + id + "/date-of-birth",
                HttpMethod.PATCH,
                new HttpEntity<>(patch, jsonHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("dateOfBirth")).isEqualTo("1985-08-15");
    }

    @Test
    @DisplayName("DELETE /api/v1/patients/{id} returns 204 and soft-deletes so a later GET no longer includes it")
    void deletePatient_softDeletesAndDisappearsFromList() {

        String email = "del-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<Map> created = restTemplate.postForEntity(
                "/api/v1/patients", new HttpEntity<>(createBody(email), jsonHeaders()), Map.class);
        String id = (String) created.getBody().get("id");

        ResponseEntity<Void> deleteResponse =
                restTemplate.exchange("/api/v1/patients/" + id, HttpMethod.DELETE, null, Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List> list = restTemplate.getForEntity("/api/v1/patients", List.class);
        assertThat(list.getBody()).isNotNull();
        assertThat(list.getBody().stream()
                        .anyMatch(p -> ((Map<?, ?>) p).get("id").equals(id)))
                .as("deleted patient should not appear in GET /api/v1/patients")
                .isFalse();
    }

    @Test
    @DisplayName("GET /api/v1/patients/{id} on a non-existent id returns 404")
    void getNonExistentPatient_notUsedBecauseNoGetByIdEndpointYet() {

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/patients/" + UUID.randomUUID(),
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "name", "Ghost",
                                "email", "ghost@example.com",
                                "address", "nowhere at all here"),
                        jsonHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("title")).asString().contains("Patient not found");
    }
}
