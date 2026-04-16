package com.kim.fraudengine.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void tokens_are_valid_for_expected_issuer_and_audience() {
        JwtService jwtService =
                new JwtService(
                        SECRET,
                        60,
                        "fraud-detection-engine-test",
                        "fraud-detection-engine-api-test");

        String token = jwtService.generateToken("analyst", List.of("alerts:read:all"));

        assertThat(jwtService.isTokenValid(token, "analyst")).isTrue();
    }

    @Test
    void tokens_from_another_issuer_are_rejected() {
        JwtService issuingService =
                new JwtService(SECRET, 60, "other-service", "fraud-detection-engine-api-test");
        JwtService validatingService =
                new JwtService(
                        SECRET,
                        60,
                        "fraud-detection-engine-test",
                        "fraud-detection-engine-api-test");

        String token = issuingService.generateToken("analyst", List.of("alerts:read:all"));

        assertThat(validatingService.isTokenValid(token, "analyst")).isFalse();
    }

    @Test
    void tokens_for_another_audience_are_rejected() {
        JwtService issuingService =
                new JwtService(SECRET, 60, "fraud-detection-engine-test", "some-other-audience");
        JwtService validatingService =
                new JwtService(
                        SECRET,
                        60,
                        "fraud-detection-engine-test",
                        "fraud-detection-engine-api-test");

        String token = issuingService.generateToken("analyst", List.of("alerts:read:all"));

        assertThat(validatingService.isTokenValid(token, "analyst")).isFalse();
    }
}
