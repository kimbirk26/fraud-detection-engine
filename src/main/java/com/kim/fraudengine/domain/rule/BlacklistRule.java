package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.RuleConfiguration;
import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionContext;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.outbound.RuleConfigurationProvider;
import java.util.Objects;
import java.util.Set;

/** Flags transactions involving known fraudulent merchants. */
public final class BlacklistRule implements FraudRule {

    private static final String RULE_NAME = "BLACKLIST_MATCH";

    private final RuleConfigurationProvider configProvider;
    private final Set<String> defaultBlacklistedMerchantIds;
    private final int defaultScore;

    public BlacklistRule(
            RuleConfigurationProvider configProvider,
            Set<String> defaultBlacklistedMerchantIds,
            int defaultScore) {
        this.configProvider = configProvider;
        this.defaultBlacklistedMerchantIds =
                Set.copyOf(Objects.requireNonNull(defaultBlacklistedMerchantIds));
        this.defaultScore = defaultScore;
    }

    @Override
    public RuleResult evaluate(TransactionContext transactionContext) {
        RuleConfiguration config = configProvider.getConfiguration(RULE_NAME).orElse(null);
        Set<String> blacklistedMerchantIds =
                config != null ? merchantIds(config) : defaultBlacklistedMerchantIds;
        int score = config != null ? config.score() : defaultScore;

        TransactionEvent transactionEvent = transactionContext.transaction();
        String merchantId = transactionEvent.merchantId();
        if (merchantId != null && blacklistedMerchantIds.contains(merchantId)) {
            return RuleResult.flag(
                    ruleName(),
                    Severity.HIGH,
                    "Merchant " + transactionEvent.merchantId() + " is blacklisted",
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

    private Set<String> merchantIds(RuleConfiguration config) {
        String ids = config.parameters().get("merchantIds");
        if (ids == null || ids.isBlank()) {
            return defaultBlacklistedMerchantIds;
        }
        return Set.of(ids.split(","));
    }
}
