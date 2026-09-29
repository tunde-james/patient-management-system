package com.devtunde.authservice.exception;

public class BreachedPasswordException extends RuntimeException {

    public BreachedPasswordException(String message) {
        super(message);
    }
}
