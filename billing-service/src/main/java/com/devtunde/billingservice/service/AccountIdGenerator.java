package com.devtunde.billingservice.service;

import org.springframework.stereotype.Component;

@Component
public interface AccountIdGenerator {

    String generate();
}
