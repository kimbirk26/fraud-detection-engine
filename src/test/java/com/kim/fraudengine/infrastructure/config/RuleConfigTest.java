package com.kim.fraudengine.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.kim.fraudengine.domain.model.TransactionCategory;
import com.kim.fraudengine.domain.model.TransactionContext;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.rule.RuleEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = RuleConfig.class)
class RuleConfigTest {

    private static final ZoneId JOHANNESBURG = ZoneId.of("Africa/Johannesburg");

    @Autowired private RuleEngine ruleEngine;

    @Test
    void shouldUseConfiguredAmountThresholds() {
        TransactionContext context =
                context(
                        new BigDecimal("15000.00"),
                        "MERCH_OK_001",
                        "ZA",
                        timestampAtSast(2024, 1, 1, 10, 0),
                        0);

        assertThat(ruleEngine.evaluate(context))
                .extracting(result -> result.ruleName())
                .containsExactly("AMOUNT_THRESHOLD");
    }

    @Test
    void shouldUseConfiguredBlacklistValues() {
        TransactionContext context =
                context(
                        new BigDecimal("100.00"),
                        "MERCH_FRAUD_001",
                        "ZA",
                        timestampAtSast(2024, 1, 1, 10, 0),
                        0);

        assertThat(ruleEngine.evaluate(context))
                .extracting(result -> result.ruleName())
                .containsExactly("BLACKLIST_MATCH");
    }

    @Test
    void shouldUseConfiguredOutOfHoursWindow() {
        TransactionContext context =
                context(
                        new BigDecimal("100.00"),
                        "MERCH_OK_002",
                        "ZA",
                        timestampAtSast(2024, 1, 1, 2, 15),
                        0);

        assertThat(ruleEngine.evaluate(context))
                .extracting(result -> result.ruleName())
                .containsExactly("OUT_OF_HOURS");
    }

    @Test
    void shouldUseConfiguredVelocityRule() {
        TransactionContext context =
                context(
                        new BigDecimal("100.00"),
                        "MERCH_OK_003",
                        "ZA",
                        timestampAtSast(2024, 1, 1, 10, 0),
                        5);

        assertThat(ruleEngine.evaluate(context))
                .singleElement()
                .satisfies(
                        result -> {
                            assertThat(result.ruleName()).isEqualTo("VELOCITY_CHECK");
                            assertThat(result.reason())
                                    .contains("6 transactions")
                                    .contains("10 minutes");
                        });
    }

    private TransactionContext context(
            BigDecimal amount,
            String merchantId,
            String countryCode,
            Instant timestamp,
            long recentTransactionCount) {
        return new TransactionContext(
                new TransactionEvent(
                        UUID.randomUUID(),
                        "CUST001",
                        amount,
                        merchantId,
                        "Test Merchant",
                        TransactionCategory.ONLINE_PURCHASE,
                        "ZAR",
                        countryCode,
                        timestamp),
                recentTransactionCount);
    }

    private Instant timestampAtSast(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(
                        LocalDate.of(year, month, day), LocalTime.of(hour, minute), JOHANNESBURG)
                .toInstant();
    }
}
