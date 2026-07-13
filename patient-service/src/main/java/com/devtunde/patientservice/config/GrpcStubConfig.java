package com.devtunde.patientservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import billing.BillingServiceGrpc;
import io.grpc.ManagedChannel;

@Configuration
public class GrpcStubConfig {

    @Bean
    public BillingServiceGrpc.BillingServiceBlockingStub billingServiceBlockingStub(
            ManagedChannel billingServiceChannel) {
        return BillingServiceGrpc.newBlockingStub(billingServiceChannel);
    }
}
