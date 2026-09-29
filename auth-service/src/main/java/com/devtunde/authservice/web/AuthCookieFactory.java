package com.devtunde.authservice.web;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.devtunde.authservice.config.CookieProperties;
import com.devtunde.authservice.config.RefreshProperties;
import com.devtunde.authservice.service.JwtService;

@Component
public class AuthCookieFactory {

    public static final String ACCESS_COOKIE = "auth_token";
    public static final String REFRESH_COOKIE = "auth_refresh_token";

    private final JwtService jwtService;
    private final RefreshProperties refreshProperties;
    private final CookieProperties cookie;

    public AuthCookieFactory(JwtService jwtService, RefreshProperties refreshProperties, CookieProperties cookie) {
        this.jwtService = jwtService;
        this.refreshProperties = refreshProperties;
        this.cookie = cookie;
    }

    public ResponseCookie access(String token) {

        return ResponseCookie.from(ACCESS_COOKIE, token)
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(jwtService.accessTokenTtlSeconds())
                .build();
    }

    public ResponseCookie refresh(String token) {

        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite("Lax")
                .path("/api/v1/auth/refresh")
                .maxAge(refreshProperties.rollingTtl())
                .build();
    }

    public ResponseCookie clearedAccess() {

        return ResponseCookie.from(ACCESS_COOKIE, "")
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }

    public ResponseCookie clearedRefresh() {

        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite("Lax")
                .path("/api/v1/auth/refresh")
                .maxAge(0)
                .build();
    }
}
