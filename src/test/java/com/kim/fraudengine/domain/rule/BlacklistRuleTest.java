package com.kim.fraudengine.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.kim.fraudengine.domain.model.*;
import com.kim.fraudengine.domain.port.outbound.RuleConfigurationProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BlacklistRuleTest {

    private static final RuleConfigurationProvider EMPTY_PROVIDER = List::of;

    private final BlacklistRule rule =
            new BlacklistRule(EMPTY_PROVIDER, Set.of("MERCH_BAD_001", "MERCH_BAD_002"), 50);

    @Test
    void shouldPassForCleanMerchant() {
        TransactionEvent tx = transaction("MERCH_LEGIT");
        RuleResult result = rule.evaluate(context(tx));

        assertThat(result.triggered()).isFalse();
        assertThat(result.score()).isZero();
    }

    @Test
    void shouldFlagBlacklistedMerchant() {
        TransactionEvent tx = transaction("MERCH_BAD_001");
        RuleResult result = rule.evaluate(context(tx));

        assertThat(result.triggered()).isTrue();
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
        assertThat(result.reason()).contains("MERCH_BAD_001");
        assertThat(result.score()).isEqualTo(50);
    }

    @Test
    void shouldUseConfigProviderMerchantIdsWhenPresent() {
        RuleConfigurationProvider provider =
                () ->
                        List.of(
                                new RuleConfiguration(
                                        "BLACKLIST_MATCH",
                                        true,
                                        60,
                                        Map.of("merchantIds", "MERCH_DYNAMIC_001")));
        BlacklistRule dynamicRule =
                new BlacklistRule(provider, Set.of("MERCH_BAD_001"), 50);

        RuleResult result = dynamicRule.evaluate(context(transaction("MERCH_DYNAMIC_001")));
        assertThat(result.triggered()).isTrue();
        assertThat(result.score()).isEqualTo(60);
    }

    @Test
    void shouldReportDisabledWhenConfigSaysSo() {
        RuleConfigurationProvider provider =
                () ->
                        List.of(
                                new RuleConfiguration(
                                        "BLACKLIST_MATCH", false, 50, Map.of()));
        BlacklistRule disabledRule =
                new BlacklistRule(provider, Set.of("MERCH_BAD_001"), 50);

        assertThat(disabledRule.isEnabled()).isFalse();
    }

    private TransactionContext context(TransactionEvent tx) {
        return new TransactionContext(tx, 0);
    }

    private TransactionEvent transaction(String merchantId) {
        return new TransactionEvent(
                UUID.randomUUID(),
                "CUST001",
                BigDecimal.TEN,
                merchantId,
                "Some Merchant",
                TransactionCategory.ONLINE_PURCHASE,
                "ZAR",
                "ZA",
                Instant.parse("2024-01-01T10:00:00Z"));
    }
}
