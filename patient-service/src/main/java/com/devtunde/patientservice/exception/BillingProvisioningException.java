package com.devtunde.patientservice.exception;

import io.grpc.Status;

public class BillingProvisioningException extends RuntimeException {

    private final Status.Code statusCode;

    public BillingProvisioningException(Status.Code statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public BillingProvisioningException(Status.Code statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Status.Code getStatusCode() {
        return statusCode;
    }
}
