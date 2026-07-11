package com.devtunde.billingservice.grpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import billing.BillingResponse;
import billing.BillingServiceGrpc.BillingServiceImplBase;
import com.devtunde.billingservice.service.BillingService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class BillingGrpcService extends BillingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(BillingGrpcService.class);

    private final BillingService billingService;

    public BillingGrpcService(BillingService billingService) {
        this.billingService = billingService;
    }

    @Override
    public void createBillingAccount(
            billing.BillingRequest billingRequest, StreamObserver<BillingResponse> responseObserver) {

        log.info("createBillingAccount request received {}", billingRequest);

        BillingService.ProvisioningResult result = billingService.provision(
                billingRequest.getPatientId(), billingRequest.getName(), billingRequest.getEmail());

        BillingResponse response = BillingResponse.newBuilder()
                .setAccountId(result.account().getAccountId())
                .setStatus(result.account().getStatus().name())
                .setCreated(result.created())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
