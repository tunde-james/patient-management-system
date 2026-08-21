package com.devtunde.patientservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.devtunde.patientservice.config.BillingReconciliationConfig;
import com.devtunde.patientservice.config.BillingServiceConfig;
import com.devtunde.patientservice.kafka.KafkaTopicsConfig;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({BillingServiceConfig.class, BillingReconciliationConfig.class, KafkaTopicsConfig.class})
public class PatientServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientServiceApplication.class, args);
    }
}
