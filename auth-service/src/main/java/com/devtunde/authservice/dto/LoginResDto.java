package com.devtunde.authservice.dto;

import java.util.UUID;

public record LoginResDto(UUID id, String email, String message) {}
