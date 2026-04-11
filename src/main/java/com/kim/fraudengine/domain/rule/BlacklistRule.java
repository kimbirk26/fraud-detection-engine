package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionContext;
import com.kim.fraudengine.domain.model.TransactionEvent;

import java.util.Objects;
import java.util.Set;

/**
 * Flags transactions involving known fraudulent merchants.
 */
public class BlacklistRule implements FraudRule {

    private static final String RULE_NAME = "BLACKLIST_MATCH";

    private final Set<String> blacklistedMerchantIds;

    public BlacklistRule(Set<String> blacklistedMerchantIds) {
        this.blacklistedMerchantIds = Set.copyOf(Objects.requireNonNull(blacklistedMerchantIds));
    }

    @Override
    public RuleResult evaluate(TransactionContext transactionContext) {
        TransactionEvent transactionEvent = transactionContext.transaction();
        String merchantId = transactionEvent.merchantId();
        if (merchantId != null && blacklistedMerchantIds.contains(merchantId)) {
            return RuleResult.flag(
                    ruleName(),
                    Severity.HIGH,
                    "Merchant " + transactionEvent.merchantId() + " is blacklisted");
        }
        return RuleResult.pass(ruleName());
    }

    @Override
    public String ruleName() {
        return RULE_NAME;
    }
}
