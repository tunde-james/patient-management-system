package com.devtunde.apigateway;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayTimeoutIntegrationTest {

    private static HttpServer silentDownstream;

    @DynamicPropertySource
    static void pointRouteAtSilentDownstream(DynamicPropertyRegistry registry) {
        try {
            silentDownstream = HttpServer.create(new InetSocketAddress(0), 0);

            silentDownstream.createContext("/", exchange -> {
                try {
                    Thread.sleep(Duration.ofMinutes(5).toMillis());
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });

            silentDownstream.start();

            String prefix = "spring.cloud.gateway.server.webflux.routes[0]";
            registry.add(prefix + ".id", () -> "timeout-test-route");
            registry.add(
                    prefix + ".uri",
                    () -> "http://localhost:" + silentDownstream.getAddress().getPort());
            registry.add(prefix + ".predicates[0]", () -> "Path=/slow/**");

            registry.add("spring.cloud.gateway.server.webflux.httpclient.response-timeout", () -> "2s");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start silent downstream", e);
        }
    }

    @AfterAll
    static void stopSilentDownstream() {
        if (silentDownstream != null) {
            silentDownstream.stop(0);
        }
    }

    @Autowired
    private Environment environment;

    private WebTestClient webTestClient;

    @BeforeEach
    void bindClientToGateway() {
        String port = environment.getProperty("local.server.port", "4004");
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Test
    @DisplayName("silent backend -> gateway returns timeout error well under 10s")
    void silentBackend_timesOut() {

        long start = System.currentTimeMillis();

        webTestClient.get().uri("/slow/resource").exchange().expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);

        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 10_000, "gateway took " + elapsed + "ms - expected ~2s reponse-timeout");
    }
}
