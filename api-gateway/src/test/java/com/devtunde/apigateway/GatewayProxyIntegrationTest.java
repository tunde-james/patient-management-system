package com.devtunde.apigateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayProxyIntegrationTest {

    static final AtomicReference<String> LAST_RECEIVED_PATH = new AtomicReference<>();

    private static HttpServer downstream;

    @DynamicPropertySource
    static void pointRouteAtFakeDownstream(DynamicPropertyRegistry registry) {
        try {
            downstream = HttpServer.create(new InetSocketAddress(0), 0);
            downstream.createContext("/api/v1/patients", exchange -> {
                LAST_RECEIVED_PATH.set(exchange.getRequestURI().getPath());
                byte[] body = "{\"fromDownstream\":true}".getBytes();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            downstream.start();

            // Spring Boot rule: a List defined in multiple property sources is
            // REPLACED wholesale by the highest-precedence source — so this
            // dynamic definition must be the complete list (it replaces the
            // four YAML routes for this test context only).
            String prefix = "spring.cloud.gateway.server.webflux.routes[0]";
            registry.add(prefix + ".id", () -> "proxy-test-route");
            registry.add(
                    prefix + ".uri",
                    () -> "http://localhost:" + downstream.getAddress().getPort());
            registry.add(prefix + ".predicates[0]", () -> "Path=/api/v1/patients/**");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start fake downstream", e);
        }
    }

    @AfterAll
    static void stopDownstream() {
        if (downstream != null) {
            downstream.stop(0);
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
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Test
    @DisplayName("request through gateway reaches downstream with path UNMODIFIED")
    void proxyForwardsPathUnmodified() {

        webTestClient
                .get()
                .uri("/api/v1/patients")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json("{\"fromDownstream\":true}");

        assertEquals(
                "/api/v1/patients",
                LAST_RECEIVED_PATH.get(),
                "downstream must receive the full original path (no stripping)");
    }

    @Test
    @DisplayName("unknown route -> gateway 404s on its own authority")
    void unknownRoute_returns404() {

        webTestClient.get().uri("/no/such/route").exchange().expectStatus().isNotFound();
    }
}
