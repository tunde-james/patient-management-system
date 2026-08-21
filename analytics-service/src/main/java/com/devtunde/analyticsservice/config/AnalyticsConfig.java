package com.devtunde.analyticsservice.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalyticsConfig {

    @Bean
    public Clock analyticsClock() {
        return Clock.systemDefaultZone();
    }
}
