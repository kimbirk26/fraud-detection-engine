package com.kim.fraudengine.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class JwtService {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final long expiryMinutes;
    private final String issuer;
    private final String audience;

    public JwtService(
            @Value("${app.jwt.private-key}") String privateKeyPem,
            @Value("${app.jwt.public-key}") String publicKeyPem,
            @Value("${app.jwt.expiry-minutes:60}") long expiryMinutes,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience) {

        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            throw new IllegalArgumentException("app.jwt.private-key must not be blank");
        }
        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            throw new IllegalArgumentException("app.jwt.public-key must not be blank");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("app.jwt.issuer must not be blank");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("app.jwt.audience must not be blank");
        }
        this.privateKey = parsePrivateKey(privateKeyPem);
        this.publicKey = parsePublicKey(publicKeyPem);
        this.expiryMinutes = expiryMinutes;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String generateToken(String username, List<String> roles) {
        return generateToken(username, roles, null);
    }

    public String generateToken(String username, List<String> roles, String customerId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expiryMinutes * 60);

        var builder =
                Jwts.builder()
                        .subject(username)
                        .issuer(issuer)
                        .audience()
                        .add(audience)
                        .and()
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(expiry))
                        .id(UUID.randomUUID().toString())
                        .claim("roles", roles)
                        .signWith(privateKey, Jwts.SIG.RS256);

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
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    private static RSAPrivateKey parsePrivateKey(String pem) {
        try {
            String base64 =
                    pem.replace("-----BEGIN PRIVATE KEY-----", "")
                            .replace("-----END PRIVATE KEY-----", "")
                            .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) kf.generatePrivate(spec);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid RSA private key PEM", e);
        }
    }

    private static RSAPublicKey parsePublicKey(String pem) {
        try {
            String base64 =
                    pem.replace("-----BEGIN PUBLIC KEY-----", "")
                            .replace("-----END PUBLIC KEY-----", "")
                            .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(spec);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid RSA public key PEM", e);
        }
    }
}
