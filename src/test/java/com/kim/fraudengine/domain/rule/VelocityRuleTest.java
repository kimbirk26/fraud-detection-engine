package com.kim.fraudengine.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.kim.fraudengine.domain.model.*;
import com.kim.fraudengine.domain.port.outbound.RuleConfigurationProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VelocityRuleTest {

    private static final RuleConfigurationProvider EMPTY_PROVIDER = List::of;

    @Test
    void shouldPassWhenTransactionCountStaysWithinLimit() {
        VelocityRule rule = new VelocityRule(EMPTY_PROVIDER, 5, 10, 40);

        RuleResult result = rule.evaluate(context(4));

        assertThat(result.triggered()).isFalse();
        assertThat(result.score()).isZero();
    }

    @Test
    void shouldFlagWhenCurrentTransactionPushesCountOverLimit() {
        VelocityRule rule = new VelocityRule(EMPTY_PROVIDER, 5, 10, 40);

        RuleResult result = rule.evaluate(context(5));

        assertThat(result.triggered()).isTrue();
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
        assertThat(result.ruleName()).isEqualTo("VELOCITY_CHECK");
        assertThat(result.reason()).contains("6 transactions");
        assertThat(result.reason()).contains("limit: 5");
        assertThat(result.score()).isEqualTo(40);
    }

    @Test
    void shouldUseConfigProviderParametersWhenPresent() {
        RuleConfigurationProvider provider =
                () ->
                        List.of(
                                new RuleConfiguration(
                                        "VELOCITY_CHECK",
                                        true,
                                        99,
                                        Map.of("maxTransactions", "3", "windowMinutes", "5")));
        VelocityRule rule = new VelocityRule(provider, 5, 10, 40);

        RuleResult result = rule.evaluate(context(3));

        assertThat(result.triggered()).isTrue();
        assertThat(result.score()).isEqualTo(99);
        assertThat(result.reason()).contains("limit: 3");
    }

    @Test
    void shouldReportDisabledWhenConfigSaysSo() {
        RuleConfigurationProvider provider =
                () ->
                        List.of(
                                new RuleConfiguration(
                                        "VELOCITY_CHECK", false, 40, Map.of()));
        VelocityRule rule = new VelocityRule(provider, 5, 10, 40);

        assertThat(rule.isEnabled()).isFalse();
    }

    private TransactionContext context(long recentTransactionCount) {
        TransactionEvent tx =
                new TransactionEvent(
                        UUID.randomUUID(),
                        "CUST001",
                        BigDecimal.TEN,
                        "MERCH001",
                        "Test Merchant",
                        TransactionCategory.GROCERIES,
                        "ZAR",
                        "ZA",
                        Instant.parse("2024-01-01T10:00:00Z"));

        return new TransactionContext(tx, recentTransactionCount);
    }
}
