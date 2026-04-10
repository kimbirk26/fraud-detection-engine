package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.TransactionContext;

/**
 * Strategy pattern interface for fraud detection rules.
 */
public interface FraudRule {
    RuleResult evaluate(TransactionContext transactionContext);

    String ruleName();
}
