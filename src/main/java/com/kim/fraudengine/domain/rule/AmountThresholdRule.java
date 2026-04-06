package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionEvent;

import java.math.BigDecimal;

public class AmountThresholdRule implements FraudRule {

    private final BigDecimal highThreshold;
    private final BigDecimal mediumThreshold;

    public AmountThresholdRule(BigDecimal highThreshold, BigDecimal mediumThreshold) {
        this.highThreshold = highThreshold;
        this.mediumThreshold = mediumThreshold;
    }

    @Override
    public RuleResult evaluate(TransactionEvent transactionEvent) {
        BigDecimal amount = transactionEvent.amount();

        if (amount.compareTo(highThreshold) > 0) {
            return RuleResult.flag(ruleName(), Severity.HIGH,
                    String.format("Amount %.2f exceeds high threshold %.2f",
                            amount, highThreshold));
        }
        if (amount.compareTo(mediumThreshold) > 0) {
            return RuleResult.flag(ruleName(), Severity.MEDIUM,
                    String.format("Amount %.2f exceeds medium threshold %.2f",
                            amount, mediumThreshold));
        }
        return RuleResult.pass(ruleName());
    }

    @Override
    public String ruleName() {
        return "AMOUNT_THRESHOLD";
    }
}
