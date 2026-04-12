package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionContext;
import com.kim.fraudengine.domain.model.TransactionEvent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.math.BigDecimal;
import java.util.Locale;

public final class ForeignCountryRule implements FraudRule {

    private static final String RULE_NAME = "FOREIGN_COUNTRY";

    private final String homeCountryCode;
    private final BigDecimal minimumAmount;

    public ForeignCountryRule(String homeCountryCode, BigDecimal minimumAmount) {
        if (homeCountryCode == null || homeCountryCode.isBlank()) {
            throw new IllegalArgumentException("homeCountryCode must not be blank");
        }
        if (minimumAmount == null || minimumAmount.signum() < 0) {
            throw new IllegalArgumentException("minimumAmount must be non-null and non-negative");
        }

        this.homeCountryCode = homeCountryCode.trim().toUpperCase(Locale.ROOT);
        this.minimumAmount = minimumAmount;
    }

    @Override
    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "Country codes are ASCII-only ISO 3166-1 alpha-2; Locale.ROOT is correct here")
    public RuleResult evaluate(TransactionContext transactionContext) {
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
                        .formatted(ruleName(), homeCountryCode, normalizedCountry, transaction.amount()));
    }

    @Override
    public String ruleName() {
        return RULE_NAME;
    }
}
