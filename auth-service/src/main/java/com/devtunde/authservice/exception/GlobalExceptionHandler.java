package com.devtunde.authservice.exception;

import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.devtunde.common.exception.GlobalExceptionHandlerBase;
import com.devtunde.common.exception.ProblemDetailsFactory;

@RestControllerAdvice
public class GlobalExceptionHandler extends GlobalExceptionHandlerBase {

    public GlobalExceptionHandler() {
        super(new ProblemDetailsFactory("https://authservice/problems"));
    }
}
