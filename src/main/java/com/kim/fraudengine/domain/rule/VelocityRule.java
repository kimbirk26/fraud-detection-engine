package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.RuleConfiguration;
import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionContext;
import com.kim.fraudengine.domain.port.outbound.RuleConfigurationProvider;

public final class VelocityRule implements FraudRule {

    private static final String RULE_NAME = "VELOCITY_CHECK";

    private final RuleConfigurationProvider configProvider;
    private final int defaultMaxTransactions;
    private final int defaultWindowMinutes;
    private final int defaultScore;

    public VelocityRule(
            RuleConfigurationProvider configProvider,
            int defaultMaxTransactions,
            int defaultWindowMinutes,
            int defaultScore) {
        if (defaultMaxTransactions < 1) {
            throw new IllegalArgumentException("maxTransactions must be positive");
        }
        if (defaultWindowMinutes < 1) {
            throw new IllegalArgumentException("windowMinutes must be positive");
        }

        this.configProvider = configProvider;
        this.defaultMaxTransactions = defaultMaxTransactions;
        this.defaultWindowMinutes = defaultWindowMinutes;
        this.defaultScore = defaultScore;
    }

    @Override
    public RuleResult evaluate(TransactionContext context) {
        RuleConfiguration config = configProvider.getConfiguration(RULE_NAME).orElse(null);
        int maxTransactions =
                config != null
                        ? intParam(config, "maxTransactions", defaultMaxTransactions)
                        : defaultMaxTransactions;
        int windowMinutes =
                config != null
                        ? intParam(config, "windowMinutes", defaultWindowMinutes)
                        : defaultWindowMinutes;
        int score = config != null ? config.score() : defaultScore;

        long recentTransactionCount = context.recentTransactionCount();
        long totalTransactions = recentTransactionCount + 1;

        if (totalTransactions > maxTransactions) {
            return RuleResult.flag(
                    ruleName(),
                    Severity.HIGH,
                    "%d transactions in %d minutes (limit: %d)"
                            .formatted(totalTransactions, windowMinutes, maxTransactions),
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
}
