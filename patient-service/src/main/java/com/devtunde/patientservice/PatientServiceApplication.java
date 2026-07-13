package com.devtunde.patientservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.devtunde.patientservice.config.BillingServiceConfig;

@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties(BillingServiceConfig.class)
public class PatientServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientServiceApplication.class, args);
    }
}
