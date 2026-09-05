package com.devtunde.authservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devtunde.authservice.model.User;
import com.devtunde.authservice.service.AuthService;
import com.devtunde.authservice.service.JwtService;
import com.devtunde.common.exception.EmailAlreadyExistsException;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    private static final String REGISTER = "/api/v1/auth/register";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    private static User userWithId(UUID id, String email) {
        User user = new User(email, "{argon2}irrelevant-hash-in-slice");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(
                post(REGISTER).contentType(MediaType.APPLICATION_JSON).content("""
                {
                    "email": "%s",
                    "password": "%s"
                }
            """.formatted(email, password)));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register valid -> 201 + Location + body")
    void register_validRequest_returns201WithLocationAndBody() throws Exception {

        UUID id = UUID.randomUUID();
        when(authService.register(any())).thenReturn(userWithId(id, "jane.doe@example.com"));

        register("jane.doe@example.com", "correct-horse-battery")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("jane.doe@example.com"))
                .andExpect(jsonPath("$.message").value("Account created"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register duplicate email -> 409 problem+json")
    void register_duplicateEmail_returns409ProblemJson() throws Exception {

        when(authService.register(any())).thenThrow(new EmailAlreadyExistsException("Email already registered"));

        register("jane.doe@example.com", "correct-horse-battery")
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.type").value("https://authservice/problems/email-already-exist"))
                .andExpect(jsonPath("$.title").value("Email already exist"))
                .andExpect(jsonPath("$.detail").value("Email already registered"))
                .andExpect(jsonPath("$.instance").value(REGISTER))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register invalid email format -> 400 problem+json errors[email]")
    void register_invalidEmailFormat_returns400WithFieldError() throws Exception {

        register("not-an-email", "long-enough-password")
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Nested
    @DisplayName("Password length boundaries (D13: 8-128)")
    class PasswordBounds {

        @Test
        @DisplayName("7 chars -> 400 errors[password]")
        void sevenChars_rejected() throws Exception {

            register("boundary@example.com", "a1b2c3d")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("password"))
                    .andExpect(jsonPath("$.errors[0].message").value("Password must be between 8 and 128 characters"));
        }

        @Test
        @DisplayName("8 chars -> 201")
        void eightChars_accepted() throws Exception {

            when(authService.register(any())).thenReturn(userWithId(UUID.randomUUID(), "boundary@example.com"));

            register("boundary@example.com", "a1b2c3d4").andExpect(status().isCreated());
        }

        @Test
        @DisplayName("128 chars -> 201")
        void oneHundredTwentyEightChars_accepted() throws Exception {

            when(authService.register(any())).thenReturn(userWithId(UUID.randomUUID(), "boundary@example.com"));

            register("boundary@example.com", "a".repeat(128)).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("129 chars -> 400 errors[password]")
        void oneHundredTwentyNineChars_rejected() throws Exception {

            register("boundary@example.com", "a".repeat(129))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("password"));
        }
    }
}
