package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.RuleConfiguration;
import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionContext;
import com.kim.fraudengine.domain.port.outbound.RuleConfigurationProvider;
import java.math.BigDecimal;

public final class AmountThresholdRule implements FraudRule {

    private static final String RULE_NAME = "AMOUNT_THRESHOLD";

    private final RuleConfigurationProvider configProvider;
    private final BigDecimal defaultHighThreshold;
    private final BigDecimal defaultMediumThreshold;
    private final int defaultHighScore;
    private final int defaultMediumScore;

    public AmountThresholdRule(
            RuleConfigurationProvider configProvider,
            BigDecimal defaultHighThreshold,
            BigDecimal defaultMediumThreshold,
            int defaultHighScore,
            int defaultMediumScore) {
        if (defaultHighThreshold.compareTo(defaultMediumThreshold) <= 0) {
            throw new IllegalArgumentException(
                    "High threshold must be greater than medium threshold");
        }
        this.configProvider = configProvider;
        this.defaultHighThreshold = defaultHighThreshold;
        this.defaultMediumThreshold = defaultMediumThreshold;
        this.defaultHighScore = defaultHighScore;
        this.defaultMediumScore = defaultMediumScore;
    }

    @Override
    public RuleResult evaluate(TransactionContext transactionContext) {
        RuleConfiguration config = configProvider.getConfiguration(RULE_NAME).orElse(null);
        BigDecimal highThreshold =
                config != null
                        ? decimalParam(config, "highThreshold", defaultHighThreshold)
                        : defaultHighThreshold;
        BigDecimal mediumThreshold =
                config != null
                        ? decimalParam(config, "mediumThreshold", defaultMediumThreshold)
                        : defaultMediumThreshold;
        int highScore =
                config != null ? intParam(config, "highScore", defaultHighScore) : defaultHighScore;
        int mediumScore =
                config != null
                        ? intParam(config, "mediumScore", defaultMediumScore)
                        : defaultMediumScore;

        BigDecimal amount = transactionContext.transaction().amount();

        if (isAbove(amount, highThreshold)) {
            return RuleResult.flag(
                    RULE_NAME,
                    Severity.HIGH,
                    "Amount %s exceeds high threshold %s".formatted(amount, highThreshold),
                    highScore);
        }

        if (isAbove(amount, mediumThreshold)) {
            return RuleResult.flag(
                    RULE_NAME,
                    Severity.MEDIUM,
                    "Amount %s exceeds medium threshold %s".formatted(amount, mediumThreshold),
                    mediumScore);
        }

        return RuleResult.pass(RULE_NAME);
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

    private boolean isAbove(BigDecimal amount, BigDecimal threshold) {
        return amount.compareTo(threshold) > 0;
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

    private static BigDecimal decimalParam(
            RuleConfiguration config, String key, BigDecimal fallback) {
        String value = config.parameters().get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
