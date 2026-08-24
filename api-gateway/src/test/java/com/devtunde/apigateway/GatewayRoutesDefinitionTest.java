package com.devtunde.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SpringBootTest
class GatewayRoutesDefinitionTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    @DisplayName("all four configured routes load from application.yml")
    void loadsAllConfiguredRoutes() {

        List<String> ids = routeDefinitionLocator
                .getRouteDefinitions()
                .map(RouteDefinition::getId)
                .collectList()
                .block();

        assertThat(ids)
                .containsExactlyInAnyOrder(
                        "patient-service-route",
                        "analytics-service-route",
                        "api-docs-patient-route",
                        "api-docs-analytics-route");
    }
}
