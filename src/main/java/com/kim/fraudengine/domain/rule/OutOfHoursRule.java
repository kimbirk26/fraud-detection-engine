package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionContext;
import com.kim.fraudengine.domain.model.TransactionEvent;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public class OutOfHoursRule implements FraudRule {

    private static final String RULE_NAME = "OUT_OF_HOURS";

    private final int suspiciousHourStart;
    private final int suspiciousHourEnd;
    private final ZoneId zoneId;

    public OutOfHoursRule(int suspiciousHourStart, int suspiciousHourEnd, ZoneId zoneId) {
        this.suspiciousHourStart = suspiciousHourStart;
        this.suspiciousHourEnd = suspiciousHourEnd;
        this.zoneId = zoneId;
    }

    @Override
    public RuleResult evaluate(TransactionContext transactionContext) {
        TransactionEvent transaction = transactionContext.transaction();
        ZonedDateTime zonedTime = transaction.timestamp().atZone(zoneId);
        int hour = zonedTime.getHour();

        boolean inWindow = suspiciousHourStart < suspiciousHourEnd ? hour >= suspiciousHourStart && hour < suspiciousHourEnd // e.g. 00–05
                : hour >= suspiciousHourStart || hour < suspiciousHourEnd; // e.g. 22–06 (wraps midnight)

        if (inWindow) {
            String zoneLabel = zonedTime.getZone().getId();
            return RuleResult.flag(ruleName(), Severity.MEDIUM, String.format("Transaction at %02d:00 %s (suspicious window: %02d:00–%02d:00)", hour, zoneLabel, suspiciousHourStart, suspiciousHourEnd));
        }
        return RuleResult.pass(ruleName());
    }

    @Override
    public String ruleName() {
        return RULE_NAME;
    }
}
