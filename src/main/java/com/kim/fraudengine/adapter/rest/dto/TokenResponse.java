package com.kim.fraudengine.adapter.rest.dto;

import java.time.Instant;

public record TokenResponse(String token, String tokenType, Instant expiresAt) {
    public static TokenResponse bearer(String token, long expiryMinutes) {
        return new TokenResponse(token, "Bearer", Instant.now().plusSeconds(expiryMinutes * 60));
    }
}
