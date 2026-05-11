package com.kim.fraudengine.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.kim.fraudengine.domain.model.EvaluationOutcome;
import com.kim.fraudengine.domain.model.RuleConfiguration;
import com.kim.fraudengine.domain.model.TransactionCategory;
import com.kim.fraudengine.domain.model.TransactionContext;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.outbound.RuleConfigurationProvider;
import com.kim.fraudengine.domain.rule.RuleEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = RuleConfig.class)
@Import(RuleConfigTest.TestProviderConfig.class)
class RuleConfigTest {

    private static final ZoneId JOHANNESBURG = ZoneId.of("Africa/Johannesburg");

    @TestConfiguration
    static class TestProviderConfig {
        @Bean
        RuleConfigurationProvider ruleConfigurationProvider() {
            return List::of;
        }
    }

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

        EvaluationOutcome outcome = ruleEngine.evaluate(context);
        assertThat(outcome.triggeredResults())
                .extracting(result -> result.ruleName())
                .containsExactly("AMOUNT_THRESHOLD");
        assertThat(outcome.triggeredResults().getFirst().score()).isEqualTo(20);
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

        EvaluationOutcome outcome = ruleEngine.evaluate(context);
        assertThat(outcome.triggeredResults())
                .extracting(result -> result.ruleName())
                .containsExactly("BLACKLIST_MATCH");
        assertThat(outcome.triggeredResults().getFirst().score()).isEqualTo(50);
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

        EvaluationOutcome outcome = ruleEngine.evaluate(context);
        assertThat(outcome.triggeredResults())
                .extracting(result -> result.ruleName())
                .containsExactly("OUT_OF_HOURS");
        assertThat(outcome.triggeredResults().getFirst().score()).isEqualTo(15);
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

        EvaluationOutcome outcome = ruleEngine.evaluate(context);
        assertThat(outcome.triggeredResults())
                .singleElement()
                .satisfies(
                        result -> {
                            assertThat(result.ruleName()).isEqualTo("VELOCITY_CHECK");
                            assertThat(result.reason())
                                    .contains("6 transactions")
                                    .contains("10 minutes");
                            assertThat(result.score()).isEqualTo(40);
                        });
    }

    @Test
    void shouldCalculateTotalScoreAndThresholdExceeded() {
        // Blacklisted merchant (score=50) triggers threshold (40)
        TransactionContext context =
                context(
                        new BigDecimal("100.00"),
                        "MERCH_FRAUD_001",
                        "ZA",
                        timestampAtSast(2024, 1, 1, 10, 0),
                        0);

        EvaluationOutcome outcome = ruleEngine.evaluate(context);
        assertThat(outcome.totalScore()).isEqualTo(50);
        assertThat(outcome.thresholdExceeded()).isTrue();
    }

    @Test
    void shouldNotExceedThresholdForLowScoreRule() {
        // Out-of-hours only (score=15) should NOT exceed threshold (40)
        TransactionContext context =
                context(
                        new BigDecimal("100.00"),
                        "MERCH_OK_002",
                        "ZA",
                        timestampAtSast(2024, 1, 1, 2, 15),
                        0);

        EvaluationOutcome outcome = ruleEngine.evaluate(context);
        assertThat(outcome.totalScore()).isEqualTo(15);
        assertThat(outcome.thresholdExceeded()).isFalse();
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
