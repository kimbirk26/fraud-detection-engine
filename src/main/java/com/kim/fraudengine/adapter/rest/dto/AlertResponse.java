package com.kim.fraudengine.adapter.rest.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        UUID transactionId,
        String customerId,
        List<RuleResultResponse> triggeredRules,
        SeverityResponse highestSeverity,
        AlertStatusResponse status,
        Instant createdAt) {
    public AlertResponse {
        triggeredRules =
                List.copyOf(
                        Objects.requireNonNull(triggeredRules, "triggeredRules must not be null"));
    }

    public record RuleResultResponse(String ruleName, SeverityResponse severity, String reason) {}
}
