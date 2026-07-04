package com.devtunde.patientservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@Configuration
public class GrpcChannelConfig {

    private static final Logger log = LoggerFactory.getLogger(GrpcChannelConfig.class);

    @Bean
    public ManagedChannel billingServiceChannel(BillingServiceConfig config) {

        log.info("Creating ManagedChannel for Billing service at {}:{}", config.address(), config.grpcPort());

        return ManagedChannelBuilder.forAddress(config.address(), config.grpcPort())
                .usePlaintext()
                .build();
    }
}
