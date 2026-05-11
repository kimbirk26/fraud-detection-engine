package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.TransactionContext;

/** Strategy pattern interface for fraud detection rules. */
public interface FraudRule {
    RuleResult evaluate(TransactionContext transactionContext);

    String ruleName();

    /**
     * Whether this rule participates in evaluation. Defaults to {@code true}. Override to support
     * dynamic rule enable/disable via a {@code RuleConfigurationProvider}.
     */
    default boolean isEnabled() {
        return true;
    }
}
