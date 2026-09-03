package com.devtunde.authservice.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.devtunde.common.exception.GlobalExceptionHandlerBase;
import com.devtunde.common.exception.ProblemDetailsFactory;

@RestControllerAdvice
public class GlobalExceptionHandler extends GlobalExceptionHandlerBase {

    public GlobalExceptionHandler() {
        super(new ProblemDetailsFactory("https://authservice/problems"));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {

        ProblemDetail problemDetail = problemDetails.of(
                HttpStatus.UNAUTHORIZED,
                "invalid-credentials",
                "Invalid credentials",
                ex.getMessage(),
                request.getRequestURI());

        return problemDetails.response(HttpStatus.UNAUTHORIZED, problemDetail);
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ProblemDetail> handleAccountDisabled(
            AccountDisabledException ex, HttpServletRequest request) {

        ProblemDetail problemDetail = problemDetails.of(
                HttpStatus.FORBIDDEN, "account-disabled", "Account disabled", ex.getMessage(), request.getRequestURI());

        return problemDetails.response(HttpStatus.FORBIDDEN, problemDetail);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ProblemDetail> handleAccountLocked(AccountLockedException ex, HttpServletRequest request) {

        ProblemDetail problemDetail = problemDetails.of(
                HttpStatus.LOCKED, "account-locked", "Locked", ex.getMessage(), request.getRequestURI());

        return problemDetails.response(HttpStatus.LOCKED, problemDetail);
    }
}
