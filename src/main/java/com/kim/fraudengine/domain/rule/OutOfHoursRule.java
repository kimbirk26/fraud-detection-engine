package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.RuleConfiguration;
import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionContext;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.outbound.RuleConfigurationProvider;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class OutOfHoursRule implements FraudRule {

    private static final String RULE_NAME = "OUT_OF_HOURS";

    private final RuleConfigurationProvider configProvider;
    private final int defaultSuspiciousHourStart;
    private final int defaultSuspiciousHourEnd;
    private final ZoneId defaultZoneId;
    private final int defaultScore;

    public OutOfHoursRule(
            RuleConfigurationProvider configProvider,
            int defaultSuspiciousHourStart,
            int defaultSuspiciousHourEnd,
            ZoneId defaultZoneId,
            int defaultScore) {
        this.configProvider = configProvider;
        this.defaultSuspiciousHourStart = defaultSuspiciousHourStart;
        this.defaultSuspiciousHourEnd = defaultSuspiciousHourEnd;
        this.defaultZoneId = defaultZoneId;
        this.defaultScore = defaultScore;
    }

    @Override
    public RuleResult evaluate(TransactionContext transactionContext) {
        RuleConfiguration config = configProvider.getConfiguration(RULE_NAME).orElse(null);
        int suspiciousHourStart =
                config != null
                        ? intParam(config, "start", defaultSuspiciousHourStart)
                        : defaultSuspiciousHourStart;
        int suspiciousHourEnd =
                config != null
                        ? intParam(config, "end", defaultSuspiciousHourEnd)
                        : defaultSuspiciousHourEnd;
        ZoneId zoneId =
                config != null ? zoneParam(config, "timezone", defaultZoneId) : defaultZoneId;
        int score = config != null ? config.score() : defaultScore;

        TransactionEvent transaction = transactionContext.transaction();
        ZonedDateTime zonedTime = transaction.timestamp().atZone(zoneId);
        int hour = zonedTime.getHour();

        boolean inWindow =
                suspiciousHourStart < suspiciousHourEnd
                        ? hour >= suspiciousHourStart && hour < suspiciousHourEnd // e.g. 00-05
                        : hour >= suspiciousHourStart
                                || hour < suspiciousHourEnd; // e.g. 22-06 (wraps midnight)

        if (inWindow) {
            String zoneLabel = zonedTime.getZone().getId();
            return RuleResult.flag(
                    ruleName(),
                    Severity.MEDIUM,
                    String.format(
                            "Transaction at %02d:00 %s (suspicious window: %02d:00-%02d:00)",
                            hour, zoneLabel, suspiciousHourStart, suspiciousHourEnd),
                    score);
        }
        return RuleResult.pass(ruleName());
    }

    @Override
    public String ruleName() {
        return RULE_NAME;
    }

    @Override
    public boolean isEnabled() {
        return configProvider
                .getConfiguration(RULE_NAME)
                .map(RuleConfiguration::enabled)
                .orElse(true);
    }

    private static int intParam(RuleConfiguration config, String key, int fallback) {
        String value = config.parameters().get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static ZoneId zoneParam(RuleConfiguration config, String key, ZoneId fallback) {
        String value = config.parameters().get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return ZoneId.of(value);
        } catch (Exception e) {
            return fallback;
        }
    }
}
