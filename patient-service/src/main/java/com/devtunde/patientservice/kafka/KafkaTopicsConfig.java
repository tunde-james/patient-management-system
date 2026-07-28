package com.devtunde.patientservice.kafka;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "kafka.topics")
public record KafkaTopicsConfig(@NotBlank String patientEvent) {}
