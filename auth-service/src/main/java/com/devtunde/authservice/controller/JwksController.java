package com.devtunde.authservice.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.authservice.service.JwtService;

@RestController
@RequestMapping("/.well-known")
public class JwksController {

    private final JwtService jwtService;

    public JwksController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @GetMapping("/jwks.json")
    public Map<String, Object> jwks() {

        return jwtService.jwks().toJSONObject();
    }
}
