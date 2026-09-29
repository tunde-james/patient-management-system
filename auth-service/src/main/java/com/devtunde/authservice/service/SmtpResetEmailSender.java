package com.devtunde.authservice.service;

import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.devtunde.authservice.config.ResetProperties;

@Component
@Profile("prod")
public class SmtpResetEmailSender implements ResetEmailSender {

    private final JavaMailSender mailSender;
    private final ResetProperties reset;

    public SmtpResetEmailSender(JavaMailSender mailSender, ResetProperties reset) {
        this.mailSender = mailSender;
        this.reset = reset;
    }

    @Override
    public void send(String email, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(reset.from());
        message.setTo(email);
        message.setSubject(reset.subject());
        message.setText(reset.text()
                .replace("%link%", resetLink)
                .replace("%minutes%", String.valueOf(reset.tokenTtl().toMinutes())));

        mailSender.send(message);
    }
}
