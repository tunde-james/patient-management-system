package com.devtunde.authservice.config;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devtunde.authservice.exception.InvalidAccessTokenException;
import com.devtunde.authservice.service.JwtService;
import com.devtunde.authservice.service.RefreshTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public JwtAuthenticationFilter(JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")) {
            try {
                Jws<Claims> jws = jwtService.verify(header.substring(7));

                if (!refreshTokenService.isBlacklisted(jws.getPayload().getId())) {
                    List<?> roles = jws.getPayload().get("roles", List.class);

                    List<SimpleGrantedAuthority> authorities = roles == null || roles.isEmpty()
                            ? List.of()
                            : roles.stream()
                                    .map(role -> new SimpleGrantedAuthority(String.valueOf(role)))
                                    .toList();

                    SecurityContextHolder.getContext()
                            .setAuthentication(new UsernamePasswordAuthenticationToken(
                                    jws.getPayload().getSubject(), null, authorities));
                }
            } catch (InvalidAccessTokenException ex) {
                SecurityContextHolder.clearContext();
                logger.debug("Rejected bearer token: " + ex.getMessage());
            }
        }

        chain.doFilter(request, response);
    }
}
