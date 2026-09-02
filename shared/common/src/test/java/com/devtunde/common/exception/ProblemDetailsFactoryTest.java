package com.devtunde.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import org.junit.jupiter.api.Test;

class ProblemDetailsFactoryTest {

    private final ProblemDetailsFactory factory = new ProblemDetailsFactory("https://authservice/problems");

    @Test
    void of_setsRfc7807CoreFieldsAndTimestamp() {

        ProblemDetail detail = factory.of(
                HttpStatus.CONFLICT,
                "email-already-exist",
                "Email already exist",
                "Email already exist",
                "/api/v1/auth/register");

        assertThat(detail.getStatus()).isEqualTo(409);
        assertThat(detail.getType().toString()).isEqualTo("https://authservice/problems/email-already-exist");
        assertThat(detail.getTitle()).isEqualTo("Email already exist");
        assertThat(detail.getDetail()).isEqualTo("Email already exist");
        assertThat(detail.getInstance().toString()).isEqualTo("/api/v1/auth/register");
        assertThat(detail.getProperties()).containsKey("timestamp");
    }

    @Test
    void validationError_carriesFieldErrors() {

        ProblemDetail detail = factory.validationError(
                "/api/v1/auth/register", List.of(new FieldErrorDetail("password", "too short")));

        assertThat(detail.getStatus()).isEqualTo(400);
        assertThat(detail.getProperties().get("errors"))
                .isEqualTo(List.of(new FieldErrorDetail("password", "too short")));
    }

    @Test
    void response_usesProblemJsonMediaType() {

        ProblemDetail detail = factory.of(HttpStatus.CONFLICT, "t", "T", "d", "/x");

        ResponseEntity<ProblemDetail> response = factory.response(HttpStatus.CONFLICT, detail);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
