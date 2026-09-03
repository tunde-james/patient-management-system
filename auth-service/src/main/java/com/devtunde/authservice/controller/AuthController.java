package com.devtunde.authservice.controller;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.authservice.dto.LoginReqDto;
import com.devtunde.authservice.dto.LoginResDto;
import com.devtunde.authservice.dto.RegisterReqDto;
import com.devtunde.authservice.dto.RegisterResDto;
import com.devtunde.authservice.mapper.UserMapper;
import com.devtunde.authservice.model.User;
import com.devtunde.authservice.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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

        return ResponseEntity.ok(UserMapper.toLoginDTO(user));
    }
}
