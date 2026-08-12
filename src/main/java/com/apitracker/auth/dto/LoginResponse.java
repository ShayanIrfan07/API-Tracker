package com.apitracker.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInMs,
        String username
) {
}
