package com.devtunde.authservice.controller;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.authservice.dto.ForgotPasswordReqDto;
import com.devtunde.authservice.dto.LoginReqDto;
import com.devtunde.authservice.dto.LoginResDto;
import com.devtunde.authservice.dto.MessageResDto;
import com.devtunde.authservice.dto.RefreshReqDto;
import com.devtunde.authservice.dto.RegisterReqDto;
import com.devtunde.authservice.dto.RegisterResDto;
import com.devtunde.authservice.dto.ResetPasswordReqDto;
import com.devtunde.authservice.dto.TokenResDto;
import com.devtunde.authservice.exception.InvalidAccessTokenException;
import com.devtunde.authservice.exception.InvalidRefreshTokenException;
import com.devtunde.authservice.mapper.UserMapper;
import com.devtunde.authservice.model.User;
import com.devtunde.authservice.service.AuthService;
import com.devtunde.authservice.service.JwtService;
import com.devtunde.authservice.service.PasswordResetService;
import com.devtunde.authservice.service.RefreshTokenService;
import com.devtunde.authservice.web.AuthCookieFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieFactory authCookies;
    private final PasswordResetService passwordResetService;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            AuthCookieFactory authCookies,
            PasswordResetService passwordResetService) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.authCookies = authCookies;
        this.passwordResetService = passwordResetService;
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
        String refresToken = refreshTokenService.issue(user);

        ResponseCookie accessCookie = authCookies.access(token);
        ResponseCookie refreshCookie = authCookies.refresh(refresToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString(), refreshCookie.toString())
                .body(UserMapper.toLoginDTO(user));
    }

    @PostMapping("/token")
    public ResponseEntity<TokenResDto> token(@Valid @RequestBody LoginReqDto reqDto) {

        User user = authService.login(reqDto);
        String refreshToken = refreshTokenService.issue(user);

        return ResponseEntity.ok(
                new TokenResDto(jwtService.issue(user), "Bearer", jwtService.accessTokenTtlSeconds(), refreshToken));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResDto> refresh(
            @CookieValue(name = "auth_refresh_token", required = false) String cookieToken,
            @RequestBody(required = false) RefreshReqDto body) {

        if (cookieToken != null) {

            RefreshTokenService.Rotation rotation = refreshTokenService.rotate(cookieToken);

            ResponseCookie accessCookie = authCookies.access(jwtService.issue(rotation.user()));
            ResponseCookie refreshCookie = authCookies.refresh(rotation.refreshToken());

            return ResponseEntity.noContent()
                    .header(HttpHeaders.SET_COOKIE, accessCookie.toString(), refreshCookie.toString())
                    .build();
        }

        if (body != null && body.refreshToken() != null) {
            RefreshTokenService.Rotation rotation = refreshTokenService.rotate(body.refreshToken());

            return ResponseEntity.ok(new TokenResDto(
                    jwtService.issue(rotation.user()),
                    "Bearer",
                    jwtService.accessTokenTtlSeconds(),
                    rotation.refreshToken()));
        }

        throw new InvalidRefreshTokenException("Missing refresh token");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResDto> forgotPassword(@Valid @RequestBody ForgotPasswordReqDto reqDto) {

        passwordResetService.requestReset(reqDto.email());

        return ResponseEntity.ok(new MessageResDto("If an account exists, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResDto> resetPassword(@Valid @RequestBody ResetPasswordReqDto reqDto) {

        passwordResetService.completeReset(reqDto.token(), reqDto.newPassword());

        return ResponseEntity.ok(new MessageResDto("Password has been reset successfully."));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookieFactory.ACCESS_COOKIE, required = false) String accessCookie,
            @CookieValue(name = AuthCookieFactory.REFRESH_COOKIE, required = false) String refreshCookie,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        String rawAccessToken = accessCookie != null
                ? accessCookie
                : (authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null);

        if (rawAccessToken == null) {
            throw new InvalidAccessTokenException("Invalid access token");
        }

        Jws<Claims> jws = jwtService.verify(rawAccessToken);
        refreshTokenService.logout(jws.getPayload(), refreshCookie);

        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        authCookies.clearedAccess().toString(),
                        authCookies.clearedRefresh().toString())
                .build();
    }
}
