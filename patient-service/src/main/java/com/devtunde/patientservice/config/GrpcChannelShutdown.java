package com.devtunde.patientservice.config;

import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.ManagedChannel;

@Configuration
public class GrpcChannelShutdown {

    private static final Logger log = LoggerFactory.getLogger(GrpcChannelShutdown.class);

    @Bean
    public GrpcChannelLifecycle billingChannelLifecycle(ManagedChannel billingServiceChannel) {
        return new GrpcChannelLifecycle(billingServiceChannel);
    }

    public static class GrpcChannelLifecycle {

        private final ManagedChannel channel;

        public GrpcChannelLifecycle(ManagedChannel channel) {
            this.channel = channel;
        }

        @PreDestroy
        public void shutdown() {
            log.info("Shutting down gRPC ManagedChannel");

            channel.shutdown();

            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("ManagedChannel did not terminate in time, forcing shutdown");
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
