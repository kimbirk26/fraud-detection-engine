package com.kim.fraudengine.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.kim.fraudengine.domain.model.*;
import com.kim.fraudengine.domain.port.outbound.RuleConfigurationProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AmountThresholdRuleTest {

    private static final RuleConfigurationProvider EMPTY_PROVIDER = List::of;

    private AmountThresholdRule rule;

    @BeforeEach
    void setUp() {
        rule =
                new AmountThresholdRule(
                        EMPTY_PROVIDER,
                        new BigDecimal("50000.00"),
                        new BigDecimal("10000.00"),
                        40,
                        20);
    }

    @Test
    void shouldPassForNormalAmount() {
        TransactionContext cx = context(new BigDecimal("500.00"));
        RuleResult result = rule.evaluate(cx);
        assertThat(result.triggered()).isFalse();
        assertThat(result.score()).isZero();
    }

    @Test
    void shouldFlagMediumForAmountAboveMediumThreshold() {
        TransactionContext cx = context(new BigDecimal("15000.00"));
        RuleResult result = rule.evaluate(cx);
        assertThat(result.triggered()).isTrue();
        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.score()).isEqualTo(20);
    }

    @Test
    void shouldFlagHighForAmountAboveHighThreshold() {
        TransactionContext cx = context(new BigDecimal("75000.00"));
        RuleResult result = rule.evaluate(cx);
        assertThat(result.triggered()).isTrue();
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
        assertThat(result.score()).isEqualTo(40);
    }

    @Test
    void shouldUseConfigProviderParametersWhenPresent() {
        RuleConfigurationProvider provider =
                () ->
                        List.of(
                                new RuleConfiguration(
                                        "AMOUNT_THRESHOLD",
                                        true,
                                        99,
                                        Map.of(
                                                "highThreshold", "100000.00",
                                                "mediumThreshold", "50000.00",
                                                "highScore", "99",
                                                "mediumScore", "55")));
        AmountThresholdRule dynamicRule =
                new AmountThresholdRule(
                        provider,
                        new BigDecimal("50000.00"),
                        new BigDecimal("10000.00"),
                        40,
                        20);

        RuleResult result = dynamicRule.evaluate(context(new BigDecimal("75000.00")));
        assertThat(result.triggered()).isTrue();
        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.score()).isEqualTo(55);
    }

    @Test
    void shouldReportDisabledWhenConfigSaysSo() {
        RuleConfigurationProvider provider =
                () ->
                        List.of(
                                new RuleConfiguration(
                                        "AMOUNT_THRESHOLD", false, 40, Map.of()));
        AmountThresholdRule disabledRule =
                new AmountThresholdRule(
                        provider,
                        new BigDecimal("50000.00"),
                        new BigDecimal("10000.00"),
                        40,
                        20);

        assertThat(disabledRule.isEnabled()).isFalse();
    }

    private TransactionEvent transaction(BigDecimal amount) {
        return new TransactionEvent(
                UUID.randomUUID(),
                "CUST001",
                amount,
                "MERCH001",
                "Test Merchant",
                TransactionCategory.ONLINE_PURCHASE,
                "ZAR",
                "ZA",
                Instant.parse("2024-01-01T10:00:00Z"));
    }

    private TransactionContext context(BigDecimal amount) {
        return new TransactionContext(transaction(amount), 0);
    }
}
