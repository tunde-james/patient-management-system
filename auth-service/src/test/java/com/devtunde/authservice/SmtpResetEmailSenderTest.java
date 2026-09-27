package com.devtunde.authservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.devtunde.authservice.config.ResetProperties;
import com.devtunde.authservice.service.SmtpResetEmailSender;

class SmtpResetEmailSenderTest {

    @Test
    @DisplayName("send: message carries recipient, from address and the reset link")
    void send_buildsMessageWithLinkAndRecipient() {

        JavaMailSender mailSender = mock(JavaMailSender.class);
        ResetProperties properties = new ResetProperties(
                "http://localhost:5173",
                Duration.ofMinutes(10),
                "noreply@example.com",
                "Password reset",
                "Reset your password:\n\n%link%\n\nExpires in %minutes% minutes.");

        new SmtpResetEmailSender(mailSender, properties)
                .send("user@example.com", "http://localhost:5173/reset-password?token=abc123");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();

        assertThat(message.getTo()).containsExactly("user@example.com");

        assertThat(message.getFrom()).isEqualTo("noreply@example.com");

        assertThat(message.getSubject()).isEqualTo("Password reset");

        assertThat(message.getText())
                .isEqualTo(
                        "Reset your password:\n\nhttp://localhost:5173/reset-password?token=abc123\n\nExpires in 10 minutes.");
    }
}
