package com.devtunde.common.exception;

import java.util.List;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GlobalExceptionHandlerBase {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandlerBase.class);

    protected final ProblemDetailsFactory problemDetails;

    protected GlobalExceptionHandlerBase(ProblemDetailsFactory problemDetails) {
        this.problemDetails = problemDetails;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(
                        error.getField(), Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value")))
                .toList();

        ProblemDetail problemDetail = problemDetails.validationError(request.getRequestURI(), errors);

        return problemDetails.response(HttpStatus.BAD_REQUEST, problemDetail);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleEmailAlreadyExistsException(
            EmailAlreadyExistsException ex, HttpServletRequest request) {

        log.warn("Email address already exists: {}", ex.getMessage());

        ProblemDetail problemDetail = problemDetails.of(
                HttpStatus.CONFLICT,
                "email-already-exist",
                "Email already exist",
                ex.getMessage(),
                request.getRequestURI());

        return problemDetails.response(HttpStatus.CONFLICT, problemDetail);
    }
}
