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

        log.info(
                "Creating ManagedChannel for Billing service at {}:{} (usePlaintext={})",
                config.address(),
                config.grpcPort(),
                config.usePlaintext());

        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(config.address(), config.grpcPort());

        if (config.usePlaintext()) {
            builder.usePlaintext();
        }

        return builder.build();
    }
}
