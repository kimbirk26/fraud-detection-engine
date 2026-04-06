package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionCategory;
import com.kim.fraudengine.domain.model.TransactionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AmountThresholdRuleTest {

    private AmountThresholdRule rule;

    @BeforeEach
    void setUp() {
        rule = new AmountThresholdRule(
                new BigDecimal("50000.00"),
                new BigDecimal("10000.00")
        );
    }

    @Test
    void shouldPassForNormalAmount() {
        TransactionEvent tx = transaction(new BigDecimal("500.00"));
        RuleResult result = rule.evaluate(tx);
        assertThat(result.triggered()).isFalse();
    }

    @Test
    void shouldFlagMediumForAmountAboveMediumThreshold() {
        TransactionEvent tx = transaction(new BigDecimal("15000.00"));
        RuleResult result = rule.evaluate(tx);
        assertThat(result.triggered()).isTrue();
        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    void shouldFlagHighForAmountAboveHighThreshold() {
        TransactionEvent tx = transaction(new BigDecimal("75000.00"));
        RuleResult result = rule.evaluate(tx);
        assertThat(result.triggered()).isTrue();
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }

    private TransactionEvent transaction(BigDecimal amount) {
        return new TransactionEvent(UUID.randomUUID(), "CUST001", amount,
                "MERCH001", "Test Merchant", TransactionCategory.ONLINE_PURCHASE,
                "ZAR", "ZA", Instant.now());
    }
}
