package com.devtunde.authservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenResDto(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("refresh_token") String refreshToken) {}
