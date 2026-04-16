package com.kim.fraudengine.infrastructure.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.rate-limit.auth")
public record AuthRateLimitProperties(
        @Positive int capacity,
        @Positive int refillPerMinute,
        boolean trustForwardedHeaders,
        List<@NotBlank String> trustedProxies,
        @Positive long entryTtlMinutes,
        @Positive int maxTrackedClients,
        @Positive int cleanupInterval) {

    public AuthRateLimitProperties {
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    }

    @AssertTrue(message = "trustedProxies must be configured when trustForwardedHeaders is true") public boolean hasTrustedProxyConfiguration() {
        return !trustForwardedHeaders || !trustedProxies.isEmpty();
    }
}
