package com.kim.fraudengine.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expiryMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiry-minutes:60}") long expiryMinutes) {

        if (secret == null || secret.isBlank() || secret.length() < 32) {
            throw new IllegalArgumentException("app.jwt.secret must be at least 32 characters for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMinutes = expiryMinutes;
    }

    public String generateToken(String username, List<String> roles) {
        return generateToken(username, roles, null);
    }

    public String generateToken(String username, List<String> roles, String customerId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expiryMinutes * 60);

        var builder = Jwts.builder().subject(username).issuedAt(Date.from(now)).expiration(Date.from(expiry)).id(UUID.randomUUID().toString()).claim("roles", roles).signWith(signingKey);

        if (customerId != null && !customerId.isBlank()) {
            builder.claim("customerId", customerId);
        }

        return builder.compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, String expectedUsername) {
        try {
            String subject = extractUsername(token);
            return subject.equals(expectedUsername) && !isExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Object roles = parseClaims(token).get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    public String extractCustomerId(String token) {
        Object customerId = parseClaims(token).get("customerId");
        if (customerId instanceof String value && !value.isBlank()) {
            return value;
        }
        return null;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    private boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }
}
