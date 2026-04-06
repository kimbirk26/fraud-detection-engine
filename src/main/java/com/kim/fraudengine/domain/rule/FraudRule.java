package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.TransactionEvent;

/**
 * Strategy pattern interface for fraud detection rules.
 */
public interface FraudRule {
    RuleResult evaluate(TransactionEvent transactionEvent);
    String ruleName();
}
