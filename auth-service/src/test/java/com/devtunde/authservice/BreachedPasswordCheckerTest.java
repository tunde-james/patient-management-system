package com.devtunde.authservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.authservice.config.BreachedPasswordProperties;
import com.devtunde.authservice.service.BreachedPasswordChecker;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class BreachedPasswordCheckerTest {

    private MockWebServer server;

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Test
    @DisplayName("password whose suffix appears above the threshold is rejected")
    void aboveThreshold_isBreached() throws Exception {
        server = startServer();
        String password = "well-known-breach-victim";
        String suffix = sha1Hex(password).substring(5);

        server.enqueue(new MockResponse().setBody(suffix + ":5000\n"));

        assertThat(newChecker(true).isBreached(password)).isTrue();

        RecordedRequest request = server.takeRequest();

        assertThat(request.getPath()).startsWith("/range/").hasSize(12);

        assertThat(request.getPath()).doesNotContain(password);
    }

    @Test
    @DisplayName("suffix count at the threshold is allowed")
    void atThreshold_isAllowed() throws Exception {
        server = startServer();
        String password = "mildly-common-passphrase";
        String suffix = sha1Hex(password).substring(5);

        server.enqueue(new MockResponse().setBody(suffix + ":1000\n"));

        assertThat(newChecker(true).isBreached(password)).isFalse();
    }

    @Test
    @DisplayName("connection failure fails open")
    void serverDown_failsOpen() throws Exception {
        server = startServer();
        server.close();

        assertThat(newChecker(true).isBreached("any-password-here")).isFalse();
    }

    @Test
    @DisplayName("flag off: no HTTP call is made at all")
    void disabled_makesNoHttpCall() throws Exception {
        server = startServer();

        assertThat(newChecker(false).isBreached("anything-you-like")).isFalse();

        assertThat(server.getRequestCount()).isZero();
    }

    private MockWebServer startServer() throws IOException {
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.start();
        return mockWebServer;
    }

    private BreachedPasswordChecker newChecker(boolean enabled) {
        return new BreachedPasswordChecker(new BreachedPasswordProperties(enabled, baseUrl()));
    }

    private String baseUrl() {
        return server.url("/").toString().replaceAll("/$", "");
    }

    private static String sha1Hex(String value) throws Exception {
        return HexFormat.of()
                .withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
