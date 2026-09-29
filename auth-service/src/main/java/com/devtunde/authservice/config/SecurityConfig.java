package com.devtunde.authservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.devtunde.authservice.service.JwtService;
import com.devtunde.authservice.service.RefreshTokenService;
import com.devtunde.authservice.web.ProblemJsonSecurityHandler;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({
    LockoutProperties.class,
    JwtProperties.class,
    RefreshProperties.class,
    AdminBootstrapProperties.class,
    ResetProperties.class,
    BreachedPasswordProperties.class,
    CookieProperties.class
})
public class SecurityConfig {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public SecurityConfig(JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/token",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password")
                        .permitAll()
                        .requestMatchers("/.well-known/jwks.json")
                        .permitAll()
                        .requestMatchers("/error", "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, refreshTokenService),
                        UsernamePasswordAuthenticationFilter.class);

        ProblemJsonSecurityHandler securityHandler = new ProblemJsonSecurityHandler();
        http.exceptionHandling(
                ex -> ex.authenticationEntryPoint(securityHandler).accessDeniedHandler(securityHandler));

        return http.build();
    }
}
