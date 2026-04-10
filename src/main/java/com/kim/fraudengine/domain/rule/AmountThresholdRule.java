package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionContext;

import java.math.BigDecimal;

public class AmountThresholdRule implements FraudRule {

    private static final String RULE_NAME = "AMOUNT_THRESHOLD";

    private final BigDecimal highThreshold;
    private final BigDecimal mediumThreshold;

    public AmountThresholdRule(BigDecimal highThreshold, BigDecimal mediumThreshold) {
        if (highThreshold.compareTo(mediumThreshold) <= 0) {
            throw new IllegalArgumentException("High threshold must be greater than medium threshold");
        }
        this.highThreshold = highThreshold;
        this.mediumThreshold = mediumThreshold;
    }

    @Override
    public RuleResult evaluate(TransactionContext transactionContext) {
        BigDecimal amount = transactionContext.transaction().amount();

        if (isAbove(amount, highThreshold)) {
            return RuleResult.flag(
                    RULE_NAME,
                    Severity.HIGH,
                    String.format("Amount %s exceeds high threshold %s", amount, highThreshold));
        }

        if (isAbove(amount, mediumThreshold)) {
            return RuleResult.flag(
                    RULE_NAME,
                    Severity.MEDIUM,
                    String.format("Amount %s exceeds medium threshold %s", amount, mediumThreshold));
        }

        return RuleResult.pass(RULE_NAME);
    }

    @Override
    public String ruleName() {
        return RULE_NAME;
    }

    private boolean isAbove(BigDecimal amount, BigDecimal threshold) {
        return amount.compareTo(threshold) > 0;
    }
}
