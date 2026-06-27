package com.devtunde.patientservice.exception;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

public class ApiProblemDetails {

    private static final String PROBLEM_BASE_URL = "https://patientservice/problems";

    private ApiProblemDetails() {}

    @SuppressWarnings("null")
    public static ResponseEntity<ProblemDetail> response(HttpStatus status, ProblemDetail problemDetail) {

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @SuppressWarnings("null")
    public static ProblemDetail validationError(String instance, List<FieldErrorDetail> errors) {

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/validation-error"));
        problemDetail.setTitle("Validation failed");
        problemDetail.setDetail("One or more fields are invalid.");
        problemDetail.setInstance(URI.create(instance));
        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }

    @SuppressWarnings("null")
    public static ProblemDetail conflict(String instance, String type, String title, String detail) {

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);

        problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/" + type));
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setInstance(URI.create(instance));

        return problemDetail;
    }

    @SuppressWarnings("null")
    public static ProblemDetail notFound(String instance, String type, String title, String detail) {

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/" + type));
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setInstance(URI.create(instance));

        return problemDetail;
    }

    @SuppressWarnings("null")
    public static ProblemDetail badRequest(String instance, String type, String title, String detail) {

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/" + type));
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setInstance(URI.create(instance));

        return problemDetail;
    }

    @SuppressWarnings("null")
    public static ProblemDetail unauthorized(String uri, String errorCode, String title, String detail) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
        problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/" + errorCode));
        problemDetail.setTitle(title);
        problemDetail.setInstance(URI.create(uri));

        return problemDetail;
    }
}
