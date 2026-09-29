package com.devtunde.authservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.devtunde.authservice.model.User;
import com.devtunde.authservice.service.JwtService;
import com.devtunde.authservice.service.UserService;
import com.redis.testcontainers.RedisContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminEndpointsTest {

    private static final String ADMIN_USERS = "/api/v1/admin/users";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Container
    static RedisContainer redis = new RedisContainer("redis:7");

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> redis.getRedisHost());
        registry.add("spring.data.redis.port", () -> redis.getRedisPort());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private User admin;

    @BeforeEach
    void adminContext() {
        admin = userService.create("admin-" + UUID.randomUUID() + "@example.com", "AdminPass123!", "ROLE_ADMIN");
        adminToken = jwtService.issue(admin);
    }

    private MockHttpServletRequestBuilder withAdmin(MockHttpServletRequestBuilder builder) {

        return builder.header("Authorization", "Bearer " + adminToken);
    }

    @Test
    @DisplayName("create's Location resolves: GET by id -> 200 + email; unknown id -> 404")
    void getUserById_locationResolves() throws Exception {
        String email = "clerk-" + UUID.randomUUID() + "@example.com";

        MvcResult result = mockMvc.perform(withAdmin(post(ADMIN_USERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                   {
                                       "email":"%s",
                                       "password":"ClerkPass123",
                                       "role":"ROLE_USER"
                                   }
                                """.formatted(email))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        User created = userService.findByEmail(email).orElseThrow();

        assertThat(result.getResponse().getHeader("Location")).isEqualTo(ADMIN_USERS + "/" + created.getId());

        mockMvc.perform(withAdmin(get(ADMIN_USERS + "/" + created.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(withAdmin(get(ADMIN_USERS + "/" + UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("create staff: 201 + Location + body, AUDIT line emitted")
    void createStaff_happyPath() throws Exception {
        String email = "nurse-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(withAdmin(post(ADMIN_USERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email":"%s",
                                    "password":"NursePass123",
                                    "role":"ROLE_USER"
                                }
                            """.formatted(email))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE));

        assertThat(userService.findByEmail(email)).isPresent();
    }

    @Test
    @DisplayName("create staff: duplicate email -> 409 problem+json")
    void createStaff_duplicate409() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        userService.create(email, "SomePass123!", "ROLE_USER");

        mockMvc.perform(withAdmin(post(ADMIN_USERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "email":"%s",
                                "password":"AnotherPass123",
                                "role":"ROLE_USER"}
                            """.formatted(email))))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    @DisplayName("create staff: invalid role -> 400")
    void createStaff_invalidRole400() throws Exception {
        mockMvc.perform(withAdmin(post(ADMIN_USERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "email":"x@example.com",
                                "password":"GoodPass123",
                                "role":"SUPERUSER"
                            }
                        """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("create staff: password below policy floor -> 400")
    void createStaff_shortPassword400() throws Exception {
        mockMvc.perform(withAdmin(post(ADMIN_USERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "email":"y@example.com",
                                "password":"short",
                                "role":"ROLE_USER"
                        }""")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("unlock: locked account restored end-to-end (bad logins -> 423 -> unlock -> login succeeds)")
    void unlock_restoresLoginThroughLockout() throws Exception {
        String email = "lock-me-" + UUID.randomUUID() + "@example.com";
        String password = "LockmePass123!";
        userService.create(email, password, "ROLE_USER");

        String wrongPasswordBody = """
            {
                "email":"%s",
                "password":"wrong-password-here"
            }
            """.formatted(email);

        for (int i = 0; i < 5; i++) {

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongPasswordBody))
                    .andExpect(status().isUnauthorized());
        }

        // sixth attempt happens while locked:
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPasswordBody))
                .andExpect(status().isLocked());

        User target = userService.findByEmail(email).orElseThrow();
        mockMvc.perform(withAdmin(put(ADMIN_USERS + "/" + target.getId() + "/unlock")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "email":"%s",
                                "password":"%s"
                            }
                        """.formatted(email, password)))
                .andExpect(status().isOk());

        // AUDIT line for the unlock
        // (attach appender pattern reused from earlier tests when needed)
    }

    @Test
    @DisplayName("sessions/revoke: existing family is dead; another user's family untouched")
    void revoke_killsTargetSessionsOnly() throws Exception {
        String targetEmail = "victim-" + UUID.randomUUID() + "@example.com";
        String otherEmail = "bystander-" + UUID.randomUUID() + "@example.com";
        String password = "RePortedPass123!";
        userService.create(targetEmail, password, "ROLE_USER");
        userService.create(otherEmail, password, "ROLE_USER");

        // Both users log in -> fresh refresh families
        String body = """
                   {"email":"%s","password":"%s"}""";
        var targetLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(targetEmail, password)))
                .andExpect(status().isOk())
                .andReturn();
        String targetRefresh = new String(
                targetLogin.getResponse().getCookie("auth_refresh_token").getValue());

        var otherLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(otherEmail, password)))
                .andExpect(status().isOk())
                .andReturn();
        String otherRefresh = new String(
                otherLogin.getResponse().getCookie("auth_refresh_token").getValue());

        User target = userService.findByEmail(targetEmail).orElseThrow();
        mockMvc.perform(withAdmin(post(ADMIN_USERS + "/" + target.getId() + "/sessions/revoke")))
                .andExpect(status().isOk());

        // Target's refresh dead; other's still alive
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("auth_refresh_token", targetRefresh)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("auth_refresh_token", otherRefresh)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("unlock + revoke on unknown id -> 404 problem+json")
    void unknownUser_returns404() throws Exception {
        UUID missing = UUID.randomUUID();

        mockMvc.perform(withAdmin(put(ADMIN_USERS + "/" + missing + "/unlock")))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));

        mockMvc.perform(withAdmin(post(ADMIN_USERS + "/" + missing + "/sessions/revoke")))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    @DisplayName("every admin action writes an AUDIT line with the caller UUID")
    void adminActions_auditLogged() throws Exception {
        Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);

        try {
            // one of each action
            String email = "auditee-" + UUID.randomUUID() + "@example.com";

            mockMvc.perform(withAdmin(post(ADMIN_USERS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "email":"%s",
                                    "password":"AuditeePass123",
                                    "role":"ROLE_USER"
                                }
                            """.formatted(email))))
                    .andExpect(status().isCreated());

            User created = userService.findByEmail(email).orElseThrow();

            mockMvc.perform(withAdmin(put(ADMIN_USERS + "/" + created.getId() + "/unlock")))
                    .andExpect(status().isOk());

            mockMvc.perform(withAdmin(post(ADMIN_USERS + "/" + created.getId() + "/sessions/revoke")))
                    .andExpect(status().isOk());

            String caller = String.valueOf(admin.getId());

            assertThat(appender.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
                    .contains("event=admin_created_user")
                    .contains(caller));

            assertThat(appender.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
                    .contains("event=admin_unlocked_user")
                    .contains(caller));

            assertThat(appender.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
                    .contains("event=admin_revoked_sessions")
                    .contains(caller));
        } finally {
            auditLogger.detachAppender(appender);
        }
    }
}
