package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionContext;

public final class VelocityRule implements FraudRule {

    private static final String RULE_NAME = "VELOCITY_CHECK";

    private final int maxTransactions;
    private final int windowMinutes;

    public VelocityRule(int maxTransactions, int windowMinutes) {
        if (maxTransactions < 1) {
            throw new IllegalArgumentException("maxTransactions must be positive");
        }
        if (windowMinutes < 1) {
            throw new IllegalArgumentException("windowMinutes must be positive");
        }

        this.maxTransactions = maxTransactions;
        this.windowMinutes = windowMinutes;
    }

    @Override
    public RuleResult evaluate(TransactionContext context) {

        long recentTransactionCount = context.recentTransactionCount();
        long totalTransactions = recentTransactionCount + 1;

        if (totalTransactions > maxTransactions) {
            return RuleResult.flag(ruleName(), Severity.HIGH, "%d transactions in %d minutes (limit: %d)".formatted(totalTransactions, windowMinutes, maxTransactions));
        }

        return RuleResult.pass(ruleName());
    }

    @Override
    public String ruleName() {
        return RULE_NAME;
    }
}
