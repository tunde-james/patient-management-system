package com.devtunde.authservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.devtunde.authservice.model.User;
import com.devtunde.authservice.repository.UserRepository;
import com.redis.testcontainers.RedisContainer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.jwt.access-token-ttl=10s")
class AuthTokenIssuanceTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String TOKEN = "/api/v1/auth/token";
    private static final String JWKS = "/.well-known/jwks.json";
    private static final int TEST_TTL_SECONDS = 10;

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

    private User seedUser(String email, String rawPassword) {
        return userRepository.save(new User(email, passwordEncoder.encode(rawPassword)));
    }

    private ResultActions performLogin(String email, String password) throws Exception {
        return postTo(LOGIN, email, password);
    }

    private ResultActions performToken(String email, String password) throws Exception {
        return postTo(TOKEN, email, password);
    }

    private ResultActions postTo(String path, String email, String password) throws Exception {
        return mockMvc.perform(
                post(path).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                            "email": "%s",
                            "password": "%s"
                        }
                        """.formatted(email, password)));
    }

    private static String uniqueEmail() {
        return "jwt-" + UUID.randomUUID() + "@example.com";
    }

    private String obtainToken(String email, String password) throws Exception {
        MvcResult result =
                performToken(email, password).andExpect(status().isOk()).andReturn();
        return JsonMapper.shared()
                .readTree(result.getResponse().getContentAsByteArray())
                .get("access_token")
                .asString();
    }

    private static RSAPublicKey jwksPublicKey(String n, String e) throws Exception {

        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));

        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }

    @Nested
    @DisplayName("GET /.well-known/jwks.json")
    class JwksEndpoint {

        @Test
        @DisplayName("unauthenticated -> 200 with a valid RSA JWKS entry")
        void unauthenticated_returnsValidJwks() throws Exception {

            MvcResult result = mockMvc.perform(get(JWKS))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                    .andReturn();

            JsonNode key = JsonMapper.shared()
                    .readTree(result.getResponse().getContentAsByteArray())
                    .get("keys")
                    .get(0);

            assertThat(key.get("kty").asString()).isEqualTo("RSA");
            assertThat(key.get("alg").asString()).isEqualTo("RS256");
            assertThat(key.get("use").asString()).isEqualTo("sig");
            assertThat(key.get("kid").asString()).isEqualTo("auth-1");
            assertThat(key.get("n").asString()).isNotBlank();
            assertThat(key.get("e").asString()).isNotBlank();
        }

        @Test
        @DisplayName("issued token verifies against the JWKS public key with the exact claim set")
        void issuedToken_verifiesAgainstJwksPublicKey_withExactClaims() throws Exception {

            String email = uniqueEmail();
            UUID id = seedUser(email, "correct-horse-battery").getId();
            String accessToken = obtainToken(email, "correct-horse-battery");

            MvcResult jwksResult =
                    mockMvc.perform(get(JWKS)).andExpect(status().isOk()).andReturn();
            JsonNode key = JsonMapper.shared()
                    .readTree(jwksResult.getResponse().getContentAsByteArray())
                    .get("keys")
                    .get(0);
            RSAPublicKey publicKey =
                    jwksPublicKey(key.get("n").asString(), key.get("e").asString());

            Jws<Claims> jws = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(accessToken);

            assertThat(jws.getHeader().getAlgorithm()).isEqualTo("RS256");
            Claims claims = jws.getPayload();
            assertThat(jws.getHeader().get("kid")).isEqualTo("auth-1");
            assertThat(claims.keySet()).containsExactlyInAnyOrder("sub", "jti", "iat", "exp", "roles");
            assertThat(claims.getSubject()).isEqualTo(id.toString());
            assertThat(claims.get("jti", String.class)).isNotBlank();
            assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                    .isEqualTo(TEST_TTL_SECONDS * 1000);
            assertThat(claims.get("roles")).isEqualTo(List.of("ROLE_USER"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login (cookie path)")
    class LoginCookiePath {

        @Test
        @DisplayName("success -> auth_token cookie (HttpOnly/Secure/Lax/Path/Max-Age), profile body only, no session")
        void success_setsCookie_andLeadsNothingInBody() throws Exception {

            String email = uniqueEmail();
            seedUser(email, "correct-horse-battery");

            MvcResult result = performLogin(email, "correct-horse-battery")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.access_token").doesNotExist())
                    .andExpect(jsonPath("$.token").doesNotExist())
                    .andReturn();

            String setCookie = result.getResponse().getHeader("Set-Cookie");
            assertThat(setCookie)
                    .startsWith("auth_token=")
                    .contains("HttpOnly")
                    .contains("Secure")
                    .contains("SameSite=Lax")
                    .contains("Path=/")
                    .contains("Max-Age=" + TEST_TTL_SECONDS);
            assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
        }

        @Test
        @DisplayName("bad credentials -> 401 problem+json, no cookie")
        void badCredentials_returns401_andNoCookie() throws Exception {

            String email = uniqueEmail();
            seedUser(email, "correct-horse-battery");

            MvcResult result = performLogin(email, "wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .andExpect(jsonPath("$.detail").value("Invalid credentials"))
                    .andReturn();

            assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/token (Bearer path)")
    class TokenEndpoint {

        @Test
        @DisplayName("success -> Bearer token in body, expires_in matches the configured TTL, no cookie")
        void success_returnsTokenInBody_andSetsNoCookie() throws Exception {

            String email = uniqueEmail();
            seedUser(email, "correct-horse-battery");

            MvcResult result = performToken(email, "correct-horse-battery")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token_type").value("Bearer"))
                    .andExpect(jsonPath("$.expires_in").value(TEST_TTL_SECONDS))
                    .andExpect(jsonPath("$.access_token").isNotEmpty())
                    .andReturn();

            assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
        }

        @Test
        @DisplayName("bad credentials -> 401 problem+json, no token, no cookie")
        void badCredentials_returns401_andNothingElse() throws Exception {

            String email = uniqueEmail();
            seedUser(email, "correct-horse-battery");

            MvcResult result = performToken(email, "wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .andExpect(jsonPath("$.access_token").doesNotExist())
                    .andReturn();

            assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
        }
    }
}
