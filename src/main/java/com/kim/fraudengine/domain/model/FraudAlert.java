package com.kim.fraudengine.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FraudAlert(
        UUID id,
        UUID transactionId,
        String customerId,
        List<RuleResult> triggeredRules,
        Severity highestSeverity,
        AlertStatus status,
        Instant createdAt) {
    public static FraudAlert from(TransactionEvent transactionEvent, List<RuleResult> triggered) {
        Severity highest =
                triggered.stream().map(RuleResult::severity).max(Enum::compareTo).orElse(Severity.NONE);
        return new FraudAlert(
                UUID.randomUUID(),
                transactionEvent.id(),
                transactionEvent.customerId(),
                triggered,
                highest,
                AlertStatus.OPEN,
                Instant.now());
    }
}
