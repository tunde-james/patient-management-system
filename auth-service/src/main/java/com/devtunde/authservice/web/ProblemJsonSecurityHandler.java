package com.devtunde.authservice.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

public class ProblemJsonSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {

        write(response, 401, "Unauthorized", "Authentication is required");
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {

        write(response, 403, "Forbidden", "Insufficient priviledges for this operation");
    }

    private static void write(HttpServletResponse response, int status, String title, String detail)
            throws IOException {

        response.setStatus(status);
        response.setContentType("application/problem+json");

        String body = """
            {
                "type":"about:blank",
                "title":"%s",
                "status":%d,
                "detail":"%s"
            }
        """.formatted(title, status, detail);

        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
