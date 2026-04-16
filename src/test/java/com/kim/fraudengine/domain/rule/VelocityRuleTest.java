package com.kim.fraudengine.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.kim.fraudengine.domain.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VelocityRuleTest {

    @Test
    void shouldPassWhenTransactionCountStaysWithinLimit() {
        VelocityRule rule = new VelocityRule(5, 10);

        RuleResult result = rule.evaluate(context(4));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    void shouldFlagWhenCurrentTransactionPushesCountOverLimit() {
        VelocityRule rule = new VelocityRule(5, 10);

        RuleResult result = rule.evaluate(context(5));

        assertThat(result.triggered()).isTrue();
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
        assertThat(result.ruleName()).isEqualTo("VELOCITY_CHECK");
        assertThat(result.reason()).contains("6 transactions");
        assertThat(result.reason()).contains("limit: 5");
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
