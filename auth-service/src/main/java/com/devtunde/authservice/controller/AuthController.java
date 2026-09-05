package com.devtunde.authservice.controller;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.authservice.dto.LoginReqDto;
import com.devtunde.authservice.dto.LoginResDto;
import com.devtunde.authservice.dto.RegisterReqDto;
import com.devtunde.authservice.dto.RegisterResDto;
import com.devtunde.authservice.dto.TokenResDto;
import com.devtunde.authservice.mapper.UserMapper;
import com.devtunde.authservice.model.User;
import com.devtunde.authservice.service.AuthService;
import com.devtunde.authservice.service.JwtService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String AUTH_TOKEN_COOKIE = "auth_token";

    private final AuthService authService;
    private JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResDto> register(@Valid @RequestBody RegisterReqDto reqDto) {

        User user = authService.register(reqDto);

        return ResponseEntity.created(URI.create("/api/v1/users/" + user.getId()))
                .body(UserMapper.toDTO(user));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResDto> login(@Valid @RequestBody LoginReqDto reqDto) {

        User user = authService.login(reqDto);
        String token = jwtService.issue(user);

        ResponseCookie cookie = ResponseCookie.from(AUTH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(jwtService.accessTokenTtlSeconds()))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(UserMapper.toLoginDTO(user));
    }

    @PostMapping("/token")
    public ResponseEntity<TokenResDto> token(@Valid @RequestBody LoginReqDto reqDto) {

        User user = authService.login(reqDto);

        return ResponseEntity.ok(new TokenResDto(jwtService.issue(user), "Bearer", jwtService.accessTokenTtlSeconds()));
    }
}
