package com.devtunde.billingservice.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class SecureRandomAccountIdGenerator implements AccountIdGenerator {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        char[] buf = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            buf[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }

        return new String(buf);
    }
}
