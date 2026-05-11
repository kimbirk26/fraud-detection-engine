package com.kim.fraudengine.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.kim.fraudengine.domain.model.*;
import com.kim.fraudengine.domain.port.outbound.RuleConfigurationProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OutOfHoursRuleTest {

    private static final RuleConfigurationProvider EMPTY_PROVIDER = List::of;

    private final ZoneId zone = ZoneId.of("Africa/Johannesburg");
    private final OutOfHoursRule rule = new OutOfHoursRule(EMPTY_PROVIDER, 0, 5, zone, 15);

    @Test
    void shouldPassDuringBusinessHours() {
        RuleResult result = rule.evaluate(contextAt(10));
        assertThat(result.triggered()).isFalse();
        assertThat(result.score()).isZero();
    }

    @Test
    void shouldPassJustBeforeSuspiciousWindow() {
        assertThat(rule.evaluate(contextAt(23)).triggered()).isFalse();
    }

    @Test
    void shouldFlagAtMidnight() {
        RuleResult result = rule.evaluate(contextAt(0));
        assertThat(result.triggered()).isTrue();
        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.score()).isEqualTo(15);
    }

    @Test
    void shouldFlagInMiddleOfSuspiciousWindow() {
        RuleResult result = rule.evaluate(contextAt(2));
        assertThat(result.triggered()).isTrue();
        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.score()).isEqualTo(15);
    }

    @Test
    void shouldFlagAtLastHourInsideWindow() {
        assertThat(rule.evaluate(contextAt(4)).triggered()).isTrue();
    }

    @Test
    void shouldPassAtWindowEndBoundary() {
        assertThat(rule.evaluate(contextAt(5)).triggered()).isFalse();
    }

    @Test
    void shouldIncludeHourAndWindowInReason() {
        RuleResult result = rule.evaluate(contextAt(3));
        assertThat(result.reason())
                .contains("03:00")
                .contains("Africa/Johannesburg")
                .contains("00:00")
                .contains("05:00");
    }

    @Test
    void shouldUseConfiguredTimezoneInReason() {
        OutOfHoursRule utcRule = new OutOfHoursRule(EMPTY_PROVIDER, 0, 5, ZoneId.of("UTC"), 15);

        RuleResult result = utcRule.evaluate(contextAt(3));

        assertThat(result.reason()).contains("UTC").doesNotContain("SAST");
    }

    @Test
    void shouldUseConfigProviderParametersWhenPresent() {
        RuleConfigurationProvider provider =
                () ->
                        List.of(
                                new RuleConfiguration(
                                        "OUT_OF_HOURS",
                                        true,
                                        25,
                                        Map.of("start", "0", "end", "10", "timezone", "Africa/Johannesburg")));
        OutOfHoursRule dynamicRule =
                new OutOfHoursRule(provider, 22, 23, zone, 15);

        // Hour 3 is inside the provider's window (0-10), proving the provider config is used
        // rather than the default (22-23)
        RuleResult result = dynamicRule.evaluate(contextAt(3));
        assertThat(result.triggered()).isTrue();
        assertThat(result.score()).isEqualTo(25);
    }

    @Test
    void shouldReportDisabledWhenConfigSaysSo() {
        RuleConfigurationProvider provider =
                () ->
                        List.of(
                                new RuleConfiguration(
                                        "OUT_OF_HOURS", false, 15, Map.of()));
        OutOfHoursRule disabledRule =
                new OutOfHoursRule(provider, 0, 5, zone, 15);

        assertThat(disabledRule.isEnabled()).isFalse();
    }

    @Nested
    class WrapAroundWindow {

        // Mirrors application.properties: start=22, end=6 (22:00-06:00 wraps midnight)
        private final OutOfHoursRule wrappingRule =
                new OutOfHoursRule(EMPTY_PROVIDER, 22, 6, zone, 15);

        @Test
        void shouldFlagJustAfterWindowStart() {
            assertThat(wrappingRule.evaluate(contextAt(22)).triggered()).isTrue();
        }

        @Test
        void shouldFlagBeforeMidnight() {
            assertThat(wrappingRule.evaluate(contextAt(23)).triggered()).isTrue();
        }

        @Test
        void shouldFlagAtMidnight() {
            assertThat(wrappingRule.evaluate(contextAt(0)).triggered()).isTrue();
        }

        @Test
        void shouldFlagAfterMidnight() {
            assertThat(wrappingRule.evaluate(contextAt(3)).triggered()).isTrue();
        }

        @Test
        void shouldFlagAtLastHourInsideWindow() {
            assertThat(wrappingRule.evaluate(contextAt(5)).triggered()).isTrue();
        }

        @Test
        void shouldPassAtWindowEndBoundary() {
            assertThat(wrappingRule.evaluate(contextAt(6)).triggered()).isFalse();
        }

        @Test
        void shouldPassDuringBusinessHours() {
            assertThat(wrappingRule.evaluate(contextAt(14)).triggered()).isFalse();
        }

        @Test
        void shouldPassJustBeforeWindowStart() {
            assertThat(wrappingRule.evaluate(contextAt(21)).triggered()).isFalse();
        }
    }

    private TransactionEvent transactionAt(int hourSast) {
        ZonedDateTime sast =
                LocalDate.now().atTime(hourSast, 0).atZone(ZoneId.of("Africa/Johannesburg"));
        return new TransactionEvent(
                UUID.randomUUID(),
                "CUST001",
                BigDecimal.TEN,
                "MERCH001",
                "Test Merchant",
                TransactionCategory.ONLINE_PURCHASE,
                "ZAR",
                "ZA",
                sast.toInstant());
    }

    private TransactionContext contextAt(int hourSast) {
        return new TransactionContext(transactionAt(hourSast), 0);
    }
}
