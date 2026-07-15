package com.devtunde.patientservice.grpc;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc;
import com.devtunde.patientservice.config.BillingServiceConfig;
import com.devtunde.patientservice.exception.BillingProvisioningException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

@Service
public class BillingServiceGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(BillingServiceGrpcClient.class);

    private final BillingServiceGrpc.BillingServiceBlockingStub blockingStub;
    private final BillingServiceConfig config;

    public BillingServiceGrpcClient(
            BillingServiceGrpc.BillingServiceBlockingStub blockingStub, BillingServiceConfig config) {
        this.blockingStub = blockingStub;
        this.config = config;
    }

    public BillingResponse createBillingAccount(String patientId, String name, String email) {

        BillingRequest request = BillingRequest.newBuilder()
                .setPatientId(patientId)
                .setName(name)
                .setEmail(email)
                .build();

        BillingServiceGrpc.BillingServiceBlockingStub stubWithDeadline =
                blockingStub.withDeadlineAfter(config.deadlineSeconds(), TimeUnit.SECONDS);

        try {
            BillingResponse response = stubWithDeadline.createBillingAccount(request);
            log.info("Received response from billing service via GRPC: {}", response);
            return response;

        } catch (StatusRuntimeException ex) {
            Status.Code code = ex.getStatus().getCode();
            log.warn("gRPC call to billing service failed: code={}, patientId={}", code, patientId);
            throw new BillingProvisioningException(code, "Billing provisioning failed for patient " + patientId, ex);
        }
    }
}
