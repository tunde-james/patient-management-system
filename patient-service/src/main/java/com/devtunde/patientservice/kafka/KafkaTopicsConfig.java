package com.devtunde.patientservice.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "kafka.topics")
public record KafkaTopicsConfig(String patientEvent) {}
