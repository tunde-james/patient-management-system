package com.devtunde.authservice.service;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devtunde.authservice.config.BreachedPasswordProperties;

@Service
public class BreachedPasswordChecker {

    private static final int BREACH_THRESHOLD = 1000;
    private static final Logger log = LoggerFactory.getLogger(BreachedPasswordChecker.class);

    private final BreachedPasswordProperties properties;
    private final RestClient restClient;

    public BreachedPasswordChecker(BreachedPasswordProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean isBreached(String rawPassword) {

        if (!properties.enabled()) {
            return false;
        }

        try {
            String sha1 = sha1Hex(rawPassword);
            String prefix = sha1.substring(0, 5);
            String suffix = sha1.substring(5);

            String body = restClient
                    .get()
                    .uri(properties.baseUrl() + "/range/" + prefix)
                    .retrieve()
                    .body(String.class);

            if (body == null) {
                return false;
            }

            for (String line : body.split("\\R")) {
                int colon = line.indexOf(":");

                if (colon > 0 && line.substring(0, colon).equals(suffix)) {
                    return Integer.parseInt(line.substring(colon + 1).trim()) > BREACH_THRESHOLD;
                }
            }

            return false;
        } catch (Exception ex) {
            log.warn("breached-password check unavailable; failing open");
            return false;
        }
    }

    private static String sha1Hex(String value) {
        try {
            return HexFormat.of()
                    .withUpperCase()
                    .formatHex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 unavailable", ex);
        }
    }
}
