package com.devtunde.common.exception;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

public class ProblemDetailsFactory {

    private final String problemBaseUrl;

    public ProblemDetailsFactory(String problemBaseUrl) {
        this.problemBaseUrl = problemBaseUrl;
    }

    public ResponseEntity<ProblemDetail> response(HttpStatus status, ProblemDetail problemDetail) {

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    public ProblemDetail of(HttpStatus status, String type, String title, String detail, String instance) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);

        problemDetail.setType(URI.create(problemBaseUrl + "/" + type));
        problemDetail.setTitle(title);
        problemDetail.setInstance(URI.create(instance));
        problemDetail.setProperty("timestamp", Instant.now());

        return problemDetail;
    }

    public ProblemDetail validationError(String instance, List<FieldErrorDetail> errors) {

        ProblemDetail problemDetail = of(
                HttpStatus.BAD_REQUEST,
                "validation-error",
                "Validation failed",
                "One or more fields are invalid.",
                instance);
        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }
}
