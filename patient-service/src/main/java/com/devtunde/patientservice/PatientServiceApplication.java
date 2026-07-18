package com.devtunde.patientservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.devtunde.patientservice.config.BillingServiceConfig;
import com.devtunde.patientservice.kafka.KafkaTopicsConfig;

@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties({BillingServiceConfig.class, KafkaTopicsConfig.class})
public class PatientServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientServiceApplication.class, args);
    }
}
