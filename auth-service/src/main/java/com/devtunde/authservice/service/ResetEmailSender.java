package com.devtunde.authservice.service;

public interface ResetEmailSender {

    void send(String email, String resetLink);
}
