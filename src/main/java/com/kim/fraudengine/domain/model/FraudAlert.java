package com.kim.fraudengine.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record FraudAlert(
        UUID id,
        UUID transactionId,
        String customerId,
        List<RuleResult> triggeredRules,
        Severity highestSeverity,
        AlertStatus status,
        Instant createdAt) {
    public FraudAlert {
        triggeredRules =
                List.copyOf(
                        Objects.requireNonNull(triggeredRules, "triggeredRules must not be null"));
    }

    public FraudAlert withStatus(AlertStatus newStatus) {
        return new FraudAlert(
                id,
                transactionId,
                customerId,
                triggeredRules,
                highestSeverity,
                newStatus,
                createdAt);
    }

    public static FraudAlert from(TransactionEvent transactionEvent, List<RuleResult> triggered) {
        Severity highest =
                triggered.stream()
                        .map(RuleResult::severity)
                        .max(Enum::compareTo)
                        .orElse(Severity.NONE);
        return new FraudAlert(
                UUID.randomUUID(),
                transactionEvent.id(),
                transactionEvent.customerId(),
                triggered,
                highest,
                AlertStatus.OPEN,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
}
