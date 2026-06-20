package com.devtunde.patientservice.exception;

import java.util.List;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(
                        error.getField(), Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value")))
                .toList();

        ProblemDetail problemDetail = ApiProblemDetails.validationError(request.getRequestURI(), errors);

        return ApiProblemDetails.response(HttpStatus.BAD_REQUEST, problemDetail);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleEmailAlreadyExistsException(
            EmailAlreadyExistsException ex, HttpServletRequest request) {

        log.warn("Email address already exist {}", ex.getMessage());

        ProblemDetail problemDetail = ApiProblemDetails.conflict(
                request.getRequestURI(), "email-already-exist", "Email already exist", ex.getMessage());

        return ApiProblemDetails.response(HttpStatus.CONFLICT, problemDetail);
    }

    @ExceptionHandler(PatientNotFoundException.class)
    public ResponseEntity<ProblemDetail> handlePatientNotFoundException(
            PatientNotFoundException ex, HttpServletRequest request) {

        log.warn("Patient not found {}", ex.getMessage());

        ProblemDetail problemDetail = ApiProblemDetails.notFound(
                request.getRequestURI(), "patient-not-found", "Patient not found", ex.getMessage());

        return ApiProblemDetails.response(HttpStatus.NOT_FOUND, problemDetail);
    }
}
