package com.devtunde.authservice.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 2, 16384, 3);

        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("argon2", argon2);

        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder("argon2", encoders);

        delegating.setDefaultPasswordEncoderForMatches(argon2);

        return delegating;
    }
}
