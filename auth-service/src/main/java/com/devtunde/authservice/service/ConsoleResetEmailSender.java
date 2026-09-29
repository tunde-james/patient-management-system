package com.devtunde.authservice.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devtunde.authservice.config.ResetProperties;

@Component
@Profile({"dev", "test"})
public class ConsoleResetEmailSender implements ResetEmailSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleResetEmailSender.class);

    private final ResetProperties reset;

    public ConsoleResetEmailSender(ResetProperties reset) {
        this.reset = reset;
    }

    @Override
    public void send(String email, String resetLink) {
        log.info("password-reset email to={} subject={} link={}", email, reset.subject(), resetLink);
    }
}
