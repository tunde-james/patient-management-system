package com.devtunde.authservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jakarta.servlet.http.Cookie;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.devtunde.authservice.model.User;
import com.devtunde.authservice.repository.UserRepository;
import com.devtunde.authservice.service.RefreshTokenService;
import com.redis.testcontainers.RedisContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"auth.refresh.rolling-ttl=30s", "auth.refresh.absolute-ttl=60s"})
class RefreshTokenLifecycleTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String TOKEN = "/api/v1/auth/token";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String LOGOUT = "/api/v1/auth/logout";

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
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private User seedUser(String email, String rawPassword) {
        return userRepository.save(new User(email, passwordEncoder.encode(rawPassword)));
    }

    private static String uniqueEmail() {
        return "refresh-" + UUID.randomUUID() + "@example.com";
    }

    private ResultActions login(String email, String password) throws Exception {
        return postTo(LOGIN, credentialsBody(email, password));
    }

    private ResultActions token(String email, String password) throws Exception {
        return postTo(TOKEN, credentialsBody(email, password));
    }

    private static String credentialsBody(String email, String password) {
        return """
                   {
                       "email": "%s",
                       "password": "%s"
                   }
                   """.formatted(email, password);
    }

    private ResultActions postTo(String path, String json) throws Exception {
        return mockMvc.perform(
                post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions refreshWithCookie(String refreshToken) throws Exception {
        return mockMvc.perform(post(REFRESH).cookie(new Cookie("auth_refresh_token", refreshToken)));
    }

    private ResultActions refreshWithBody(String refreshToken) throws Exception {
        return postTo(REFRESH, """
                   {
                       "refreshToken": "%s"
                   }
                    """.formatted(refreshToken));
    }

    private ResultActions logoutWithCookie(String accessToken, String refreshToken) throws Exception {
        return mockMvc.perform(post(LOGOUT)
                .cookie(new Cookie("auth_token", accessToken))
                .cookie(new Cookie("auth_refresh_token", refreshToken)));
    }

    /** Returns {accessToken, refreshToken}* from a successful /login. */
    private String[] loginAndGetCookies(String email, String password) throws Exception {
        MvcResult result = login(email, password).andExpect(status().isOk()).andReturn();
        return new String[] {cookieValue(result, "auth_token"), cookieValue(result, "auth_refresh_token")};
    }

    private static String cookieValue(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie).as("cookie %s", name).isNotNull();
        return cookie.getValue();
    }

    private static String sha256Hex(String token) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {

            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static String jwtClaim(String jwt, String name) {
        String payload = jwt.split("\\.")[1];
        JsonNode node = JsonMapper.shared().readTree(Base64.getUrlDecoder().decode(payload));
        return node.get(name).asString();
    }

    /** Rewrites the stored refresh-token* record's timestamps (time machine). */
    private void ageRefreshToken(String token, long lastRotatedAgoSeconds, long absoluteExpiryInSeconds)
            throws Exception {
        String key = "auth:refresh:token:" + sha256Hex(token);
        String json = redisTemplate.opsForValue().get(key);
        assertThat(json).as("stored token record %s", key).isNotNull();

        ObjectNode node = (ObjectNode) JsonMapper.shared().readTree(json);
        if (lastRotatedAgoSeconds > 0) {
            node.put(
                    "lastRotatedAt",
                    Instant.now().minusSeconds(lastRotatedAgoSeconds).toString());
        }
        if (absoluteExpiryInSeconds != 0) {
            node.put(
                    "absoluteExpiry",
                    Instant.now().plusSeconds(absoluteExpiryInSeconds).toString());
        }

        redisTemplate.opsForValue().set(key, node.toString());
    }

    @Nested
    @DisplayName("Rotation + reuse detection")
    class Rotation {

        @Test
        @DisplayName("each refresh rotates the token; rotated-out tokens are dead")
        void rotationChain_oldTokensDead() throws Exception {
            String email = uniqueEmail();
            seedUser(email, "correct-horse-battery");
            String[] first = loginAndGetCookies(email, "correct-horse-battery");
            String previous = first[1];

            for (int i = 0; i < 3; i++) {
                MvcResult result = refreshWithCookie(previous)
                        .andExpect(status().isNoContent())
                        .andReturn();
                String rotated = cookieValue(result, "auth_refresh_token");

                assertThat(rotated).isNotEqualTo(previous);
                previous = rotated;
            }

            // The original login token was rotated out — presenting it again is a reuse:

            refreshWithCookie(first[1]).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("reusing a rotated-out token revokes the whole family, with an AUDIT line")
        void reuseDetection_revokesEntireFamily() throws Exception {
            String email = uniqueEmail();
            seedUser(email, "correct-horse-battery");
            String[] first = loginAndGetCookies(email, "correct-horse-battery");

            // The legitimate device rotates: R1 -> R2
            String r2 = cookieValue(
                    refreshWithCookie(first[1])
                            .andExpect(status().isNoContent())
                            .andReturn(),
                    "auth_refresh_token");

            Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();

            auditLogger.addAppender(appender);
            try {
                // Theft: the attacker replays the rotated-out R1
                refreshWithCookie(first[1])
                        .andExpect(status().isUnauthorized())
                        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));

                assertThat(appender.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
                        .contains("event=refresh_reuse_detected"));
            } finally {

                auditLogger.detachAppender(appender);
            }

            // The family is dead — even the legitimate R2 no longer works:
            refreshWithCookie(r2).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("replaying a rotated-out token after logout is NOT flagged as reuse (family_revoked)")
        void replayAfterLogout_notFlaggedAsReuse() throws Exception {
            String email = uniqueEmail();
            seedUser(email, "correct-horse-battery");
            String[] first = loginAndGetCookies(email, "correct-horse-battery");

            // R1 -> R2: family pointer moves to R2; R1's key lingers
            String r2 = cookieValue(
                    refreshWithCookie(first[1])
                            .andExpect(status().isNoContent())
                            .andReturn(),
                    "auth_refresh_token");

            // logout kills the family (pointer + current R2 key)
            logoutWithCookie(first[0], r2).andExpect(status().isNoContent());

            Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            auditLogger.addAppender(appender);
            try {
                refreshWithCookie(first[1])
                        .andExpect(status().isUnauthorized())
                        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));

                assertThat(appender.list)
                        .anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("event=refresh_family_revoked"));
                assertThat(appender.list)
                        .noneSatisfy(e -> assertThat(e.getFormattedMessage()).contains("refresh_reuse_detected"));
            } finally {

                auditLogger.detachAppender(appender);
            }
        }

        @Test
        @DisplayName("two concurrent rotations of the same token: exactly one wins, then the family is dead")
        void concurrentRotation_exactlyOneWinner() throws Exception {
            String email = uniqueEmail();
            seedUser(email, "correct-horse-battery");
            String token = loginAndGetCookies(email, "correct-horse-battery")[1];

            CyclicBarrier barrier = new CyclicBarrier(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<MvcResult>> results = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    results.add(pool.submit(() -> {
                        barrier.await();
                        return postTo(REFRESH, """
                                   {
                                        "refreshToken": "%s"
                                   }
                                """.formatted(token)).andReturn();
                    }));
                }

                List<MvcResult> done = new ArrayList<>();
                for (Future<MvcResult> f : results) {
                    done.add(f.get());
                }

                List<Integer> statuses =
                        done.stream().map(r -> r.getResponse().getStatus()).toList();

                assertThat(statuses).containsExactlyInAnyOrder(200, 401);

                String winnerBody = done.stream()
                        .filter(r -> r.getResponse().getStatus() == 200)
                        .findFirst()
                        .orElseThrow()
                        .getResponse()
                        .getContentAsString();
                String winnerToken = JsonMapper.shared()
                        .readTree(winnerBody.getBytes(StandardCharsets.UTF_8))
                        .get("refresh_token")
                        .asString();

                // the loser's revocation killed the family: even the winner's fresh token is now dead
                refreshWithBody(winnerToken).andExpect(status().isUnauthorized());
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("Rolling + absolute expiry")
    class Expiry {

        @Test
        @DisplayName("rolling window elapsed -> 401")
        void rollingExpiry_elapsed_returns401() throws Exception {
            String email = uniqueEmail();
            seedUser(email, "correct-horse-battery");
            String refreshToken = loginAndGetCookies(email, "correct-horse-battery")[1];

            ageRefreshToken(refreshToken, 45, 0); // 45s > 30s rolling window

            refreshWithCookie(refreshToken).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("absolute cap elapsed -> 401 even within the rolling window")
        void absoluteExpiry_elapsed_returns401() throws Exception {
            String email = uniqueEmail();
            seedUser(email, "correct-horse-battery");
            String refreshToken = loginAndGetCookies(email, "correct-horse-battery")[1];

            ageRefreshToken(refreshToken, 0, -120); // absoluteExpiry already in the past

            refreshWithCookie(refreshToken).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a valid refresh restarts the rolling window")
        void rollingWindow_restartedByRotation() throws Exception {
            String email = uniqueEmail();
            seedUser(email, "correct-horse-battery");
            String refreshToken = loginAndGetCookies(email, "correct-horse-battery")[1];

            // Age just inside the window (25s < 30s), then rotate
            ageRefreshToken(refreshToken, 25, 0);
            String rotated = cookieValue(
                    refreshWithCookie(refreshToken)
                            .andExpect(status().isNoContent())
                            .andReturn(),
                    "auth_refresh_token");

            assertThat(rotated).isNotEqualTo(refreshToken);

            // The rotated token carries a fresh window: 25s old is fine again
            ageRefreshToken(rotated, 25, 0);

            refreshWithCookie(rotated).andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("Concurrent sessions (D9 option A)")
    class ConcurrentSessions {

        @Test
        @DisplayName("two logins -> two independent families; rotating one leaves the other alive")
        void concurrentSessions_independentFamilies() throws Exception {
            String email = uniqueEmail();
            seedUser(email, "correct-horse-battery");

            String[] deviceA = loginAndGetCookies(email, "correct-horse-battery");
            String[] deviceB = loginAndGetCookies(email, "correct-horse-battery");

            assertThat(deviceA[1]).isNotEqualTo(deviceB[1]);

            // Rotating device A leaves device B's family untouched
            String aRotated = cookieValue(
                    refreshWithCookie(deviceA[1])
                            .andExpect(status().isNoContent())
                            .andReturn(),
                    "auth_refresh_token");

            refreshWithCookie(deviceB[1]).andExpect(status().isNoContent());

            // And device A's new token keeps working

            cookieValue(
                    refreshWithCookie(aRotated)
                            .andExpect(status().isNoContent())
                            .andReturn(),
                    "auth_refresh_token");
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class Logout {

        @Test
        @DisplayName("logout -> 204, jti blacklisted with bounded TTL, family gone, cookies cleared")
        void logout_revokesAccessAndFamily() throws Exception {
            String email = uniqueEmail();
            seedUser(email, "correct-horse-battery");
            String accessToken = loginAndGetCookies(email, "correct-horse-battery")[0];
            String refreshToken = loginAndGetCookies(email, "correct-horse-battery")[1];
            String jti = jwtClaim(accessToken, "jti");

            MvcResult result = logoutWithCookie(accessToken, refreshToken)
                    .andExpect(status().isNoContent())
                    .andReturn();

            // The jti is blacklisted, TTL bounded by the access token's remaining life (15m here)
            Long ttlSeconds = redisTemplate.getExpire("jwt:blacklist:" + jti);

            assertThat(ttlSeconds).isNotNull().isPositive();
            assertThat(ttlSeconds).isLessThanOrEqualTo(900);

            // The refresh family is gone
            assertThat(redisTemplate.hasKey("auth:refresh:token:" + sha256Hex(refreshToken)))
                    .isFalse();

            // The access token itself is dead — a second logout with it is a 401
            logoutWithCookie(accessToken, refreshToken).andExpect(status().isUnauthorized());

            // Both cookies are cleared on the way out
            List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
            assertThat(setCookies)
                    .anySatisfy(h -> assertThat(h).startsWith("auth_token=").contains("Max-Age=0"))
                    .anySatisfy(
                            h -> assertThat(h).startsWith("auth_refresh_token=").contains("Max-Age=0"));
        }
    }

    @Nested
    @DisplayName("Token transport updates")
    class Transports {

        @Test
        @DisplayName("/token body now carries refresh_token, still no cookies")
        void tokenBody_includesRefreshToken() throws Exception {
            String email = uniqueEmail();
            seedUser(email, "correct-horse-battery");

            MvcResult result = token(email, "correct-horse-battery")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.access_token").isNotEmpty())
                    .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                    .andReturn();

            assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
        }

        @Test
        @DisplayName("/refresh over the body transport rotates and deadens the presented token")
        void bodyTransport_rotation() throws Exception {
            String email = uniqueEmail();
            seedUser(email, "correct-horse-battery");
            String refreshToken = loginAndGetCookies(email, "correct-horse-battery")[1];

            MvcResult result = refreshWithBody(refreshToken)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.access_token").isNotEmpty())
                    .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                    .andReturn();

            assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();

            // The presented token was rotated out

            refreshWithBody(refreshToken).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("RevokeAllSessions (D9 seam)")
    class RevokeAllSessions {

        @Test
        @DisplayName("revoking a user kills every family: both devices' tokens are dead")
        void revokeAllSessions_killsAllFamilies() throws Exception {

            String email = uniqueEmail();
            User user = seedUser(email, "correct-horse-battery");
            String refreshA = loginAndGetCookies(email, "correct-horse-battery")[1];
            String refreshB = loginAndGetCookies(email, "correct-horse-battery")[1];

            refreshTokenService.revokeAllSessions(user.getId());

            refreshWithCookie(refreshA).andExpect(status().isUnauthorized());

            refreshWithCookie(refreshB).andExpect(status().isUnauthorized());

            assertThat(refreshA).isNotEqualTo(refreshB);
        }
    }
}
