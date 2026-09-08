package com.devtunde.authservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.devtunde.authservice.model.User;
import com.devtunde.authservice.service.JwtService;
import com.devtunde.authservice.service.UserService;
import com.redis.testcontainers.RedisContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthorizationTest {

    private static final String ADMIN_ENDPOINT = "/api/v1/admin/users";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Container
    static RedisContainer redis = new RedisContainer("redis:7");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserService userService;

    private String loginTokenFor(String email, String role) {
        User user = userService.create(email, "test-password-1234", role);
        return jwtService.issue(user);
    }

    @Test
    @DisplayName("no Authorization header ->  401")
    void noToken_returns401() throws Exception {
        mockMvc.perform(post(ADMIN_ENDPOINT)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ROLE_USER token -> 403  (authenticated but lacks authority)")
    void userRole_returns403() throws Exception {
        String token = loginTokenFor("staff-user@example.com", "ROLE_USER");

        mockMvc.perform(post(ADMIN_ENDPOINT).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_ADMIN token -> passes the security gate (not 401/403)")
    void adminRole_passesSecurity() throws Exception {
        String token = loginTokenFor("admin-authorized@example.com", "ROLE_ADMIN");

        mockMvc.perform(post(ADMIN_ENDPOINT).header("Authorization", "Bearer " + token))
                .andExpect(
                        result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }

    @Test
    @DisplayName("token whose jti is blacklisted (post-logout) -> 401")
    void blacklistedToken_returns401() throws Exception {
        String email = "admin-logout@example.com";
        User user = userService.create(email, "test-password-1234", "ROLE_ADMIN");
        String token = jwtService.issue(user);

        // logout blacklists the jti (cookie path)
        mockMvc.perform(post("/api/v1/auth/logout").cookie(new jakarta.servlet.http.Cookie("auth_token", token)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isNoContent());

        mockMvc.perform(post(ADMIN_ENDPOINT).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
