package com.devtunde.authservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.devtunde.authservice.model.User;
import com.devtunde.authservice.repository.UserRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"auth.security.lockout.lock-duration=2s", "auth.security.lockout.quiet-window=10s"})
class AuthLoginLockoutTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String LOCKED_DETAIL =
            "Your account has been temporarily locked due to excessive failed attempts.";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User seedUser(String email, String rawPassword) {
        return userRepository.save(new User(email, passwordEncoder.encode(rawPassword)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(
                post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("""
                   {
                       "email": "%s",
                       "password": "%s"
                   }
                   """.formatted(email, password)));
    }

    private static String uniqueEmail() {
        return "lockout-" + UUID.randomUUID() + "@example.com";
    }

    /** Drops the volatile timestamp so two
     * otherwise-identical problem bodies compare equal.
     */
    private static String normalizedBody(String body) {
        ObjectNode node = (ObjectNode) JsonMapper.shared().readTree(body.getBytes(StandardCharsets.UTF_8));
        return node.without("timestamp").toString();
    }

    @Test
    @DisplayName("5 consecutive failures -> 423 on next attempt; 5th failure is a full 401 problem+json")
    void fifthConsecutiveFailure_locksAccount() throws Exception {
        String email = uniqueEmail();
        seedUser(email, "correct-horse-battery");

        for (int i = 1; i < 5; i++) {
            login(email, "wrong-password-" + i)
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
        }

        login(email, "wrong-password-5")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.type").value("https://authservice/problems/invalid-credentials"))
                .andExpect(jsonPath("$.title").value("Invalid credentials"))
                .andExpect(jsonPath("$.detail").value("Invalid credentials"))
                .andExpect(jsonPath("$.instance").value(LOGIN))
                .andExpect(jsonPath("$.timestamp").exists());

        login(email, "correct-horse-battery")
                .andExpect(status().isLocked())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(423))
                .andExpect(jsonPath("$.type").value("https://authservice/problems/account-locked"))
                .andExpect(jsonPath("$.title").value("Locked"))
                .andExpect(jsonPath("$.detail").value(LOCKED_DETAIL))
                .andExpect(jsonPath("$.instance").value(LOGIN))
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(userRepository.findByEmail(email).orElseThrow().getLockedUntil())
                .isNotNull();
    }

    @Test
    @DisplayName("successful login returns 200 + profile and resets counter and lock")
    void successfulLogin_resetsCounterAndClearsLock() throws Exception {
        String email = uniqueEmail();
        UUID id = seedUser(email, "correct-horse-battery").getId();

        login(email, "wrong-one").andExpect(status().isUnauthorized());
        login(email, "wrong-two").andExpect(status().isUnauthorized());

        login(email, "correct-horse-battery")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value(email));

        User user = userRepository.findByEmail(email).orElseThrow();

        assertThat(user.getFailedLoginCount()).isZero();

        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("quiet window expired -> counter restarts from 1 without a successful login")
    void expiredQuietWindow_resetsCounterWithoutSuccess() throws Exception {
        String email = uniqueEmail();
        seedUser(email, "correct-horse-battery");

        login(email, "wrong-one").andExpect(status().isUnauthorized());
        login(email, "wrong-two").andExpect(status().isUnauthorized());

        assertThat(userRepository.findByEmail(email).orElseThrow().getFailedLoginCount())
                .isEqualTo(2);

        User aged = userRepository.findByEmail(email).orElseThrow();

        aged.setFailedWindowStartedAt(Instant.now().minusSeconds(30));
        userRepository.save(aged);

        login(email, "wrong-three").andExpect(status().isUnauthorized());

        assertThat(userRepository.findByEmail(email).orElseThrow().getFailedLoginCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("lock expires after the configured lock duration (2s here)")
    void lockExpires_afterLockDuration() throws Exception {
        String email = uniqueEmail();
        seedUser(email, "correct-horse-battery");

        for (int i = 1; i <= 5; i++) {
            login(email, "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        login(email, "correct-horse-battery").andExpect(status().isLocked());

        Thread.sleep(Duration.ofMillis(2_500));

        login(email, "correct-horse-battery")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    @DisplayName("unknown email vs wrong password -> identical 401 bodies modulo timestamp (D16)")
    void unknownEmail_vs_wrongPassword_identical401Bodies() throws Exception {
        String realEmail = uniqueEmail();
        seedUser(realEmail, "correct-horse-battery");

        MvcResult unknownEmail = login(uniqueEmail(), "some-password-here")
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult wrongPassword = login(realEmail, "some-password-here")
                .andExpect(status().isUnauthorized())
                .andReturn();

        String a = normalizedBody(unknownEmail.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String b = normalizedBody(wrongPassword.getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(a).isEqualTo(b).contains("Invalid credentials");
    }

    @Test
    @DisplayName("disabled account -> 403 problem+json (even with the correct password)")
    void disabledAccount_returns403() throws Exception {
        String email = uniqueEmail();
        User user = seedUser(email, "correct-horse-battery");
        user.setEnabled(false);
        userRepository.save(user);

        login(email, "correct-horse-battery")
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.type").value("https://authservice/problems/account-disabled"))
                .andExpect(jsonPath("$.title").value("Account disabled"))
                .andExpect(jsonPath("$.detail").value("Account disabled"))
                .andExpect(jsonPath("$.instance").value(LOGIN))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
