package com.kim.fraudengine.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.kim.fraudengine.domain.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AmountThresholdRuleTest {

    private AmountThresholdRule rule;

    @BeforeEach
    void setUp() {
        rule = new AmountThresholdRule(new BigDecimal("50000.00"), new BigDecimal("10000.00"));
    }

    @Test
    void shouldPassForNormalAmount() {
        TransactionContext cx = context(new BigDecimal("500.00"));
        RuleResult result = rule.evaluate(cx);
        assertThat(result.triggered()).isFalse();
    }

    @Test
    void shouldFlagMediumForAmountAboveMediumThreshold() {
        TransactionContext cx = context(new BigDecimal("15000.00"));
        RuleResult result = rule.evaluate(cx);
        assertThat(result.triggered()).isTrue();
        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    void shouldFlagHighForAmountAboveHighThreshold() {
        TransactionContext cx = context(new BigDecimal("75000.00"));
        RuleResult result = rule.evaluate(cx);
        assertThat(result.triggered()).isTrue();
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
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
