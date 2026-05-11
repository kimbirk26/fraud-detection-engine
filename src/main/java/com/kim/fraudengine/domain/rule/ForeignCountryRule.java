package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.RuleConfiguration;
import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionContext;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.outbound.RuleConfigurationProvider;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.Locale;

public final class ForeignCountryRule implements FraudRule {

    private static final String RULE_NAME = "FOREIGN_COUNTRY";

    private final RuleConfigurationProvider configProvider;
    private final String defaultHomeCountryCode;
    private final BigDecimal defaultMinimumAmount;
    private final int defaultScore;

    public ForeignCountryRule(
            RuleConfigurationProvider configProvider,
            String defaultHomeCountryCode,
            BigDecimal defaultMinimumAmount,
            int defaultScore) {
        if (defaultHomeCountryCode == null || defaultHomeCountryCode.isBlank()) {
            throw new IllegalArgumentException("homeCountryCode must not be blank");
        }
        if (defaultMinimumAmount == null || defaultMinimumAmount.signum() < 0) {
            throw new IllegalArgumentException("minimumAmount must be non-null and non-negative");
        }

        this.configProvider = configProvider;
        this.defaultHomeCountryCode = defaultHomeCountryCode.trim().toUpperCase(Locale.ROOT);
        this.defaultMinimumAmount = defaultMinimumAmount;
        this.defaultScore = defaultScore;
    }

    @Override
    @SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification =
                    "Country codes are ASCII-only ISO 3166-1 alpha-2; Locale.ROOT is correct here")
    public RuleResult evaluate(TransactionContext transactionContext) {
        RuleConfiguration config = configProvider.getConfiguration(RULE_NAME).orElse(null);
        String homeCountryCode =
                config != null
                        ? stringParam(config, "homeCountryCode", defaultHomeCountryCode)
                                .trim()
                                .toUpperCase(Locale.ROOT)
                        : defaultHomeCountryCode;
        BigDecimal minimumAmount =
                config != null
                        ? decimalParam(config, "minimumAmount", defaultMinimumAmount)
                        : defaultMinimumAmount;
        int score = config != null ? config.score() : defaultScore;

        TransactionEvent transaction = transactionContext.transaction();

        String transactionCountry = transaction.countryCode();
        if (transactionCountry == null || transactionCountry.isBlank()) {
            return RuleResult.pass(ruleName());
        }

        if (transaction.amount() == null) {
            return RuleResult.pass(ruleName());
        }

        String normalizedCountry = transactionCountry.trim().toUpperCase(Locale.ROOT);

        if (transaction.amount().compareTo(minimumAmount) < 0
                || normalizedCountry.equals(homeCountryCode)) {
            return RuleResult.pass(ruleName());
        }

        return RuleResult.flag(
                ruleName(),
                Severity.MEDIUM,
                "rule=%s | expected=%s | actual=%s | amount=%s"
                        .formatted(
                                ruleName(),
                                homeCountryCode,
                                normalizedCountry,
                                transaction.amount()),
                score);
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

    private static String stringParam(RuleConfiguration config, String key, String fallback) {
        String value = config.parameters().get(key);
        return (value != null && !value.isBlank()) ? value : fallback;
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
