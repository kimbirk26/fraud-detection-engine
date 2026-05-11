package com.kim.fraudengine.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.kim.fraudengine.domain.model.RuleConfiguration;
import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionCategory;
import com.kim.fraudengine.domain.model.TransactionContext;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.outbound.RuleConfigurationProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ForeignCountryRuleTest {

    private static final RuleConfigurationProvider EMPTY_PROVIDER = List::of;

    private ForeignCountryRule rule;

    @BeforeEach
    void setUp() {
        rule = new ForeignCountryRule(EMPTY_PROVIDER, "ZA", new BigDecimal("1000.00"), 20);
    }

    @Test
    void shouldPassForHomeCountryTransaction() {
        RuleResult result = rule.evaluate(context("ZA", "2500.00"));

        assertThat(result.triggered()).isFalse();
        assertThat(result.score()).isZero();
    }

    @Test
    void shouldPassWhenAmountIsBelowThreshold() {
        RuleResult result = rule.evaluate(context("FR", "500.00"));
        assertThat(result.triggered()).isFalse();
        assertThat(result.score()).isZero();
    }

    @Test
    void shouldFlagForeignCountryTransactionAboveThreshold() {
        RuleResult result = rule.evaluate(context("FR", "2500.00"));

        assertThat(result.triggered()).isTrue();
        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.ruleName()).isEqualTo("FOREIGN_COUNTRY");
        assertThat(result.reason())
                .contains("rule=FOREIGN_COUNTRY")
                .contains("expected=ZA")
                .contains("actual=FR");
        assertThat(result.score()).isEqualTo(20);
    }

    @Test
    void shouldPassWhenCountryCodeIsMissing() {
        RuleResult result = rule.evaluate(context(" ", "2500.00"));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    void shouldNormalizeCountryCodesWithLocaleRoot() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            ForeignCountryRule localeSafeRule =
                    new ForeignCountryRule(EMPTY_PROVIDER, "in", new BigDecimal("1000.00"), 20);

            RuleResult result = localeSafeRule.evaluate(context("IN", "2500.00"));

            assertThat(result.triggered()).isFalse();
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void shouldUseConfigProviderParametersWhenPresent() {
        RuleConfigurationProvider provider =
                () ->
                        List.of(
                                new RuleConfiguration(
                                        "FOREIGN_COUNTRY",
                                        true,
                                        30,
                                        Map.of(
                                                "homeCountryCode", "US",
                                                "minimumAmount", "500.00")));
        ForeignCountryRule dynamicRule =
                new ForeignCountryRule(provider, "ZA", new BigDecimal("1000.00"), 20);

        RuleResult result = dynamicRule.evaluate(context("FR", "600.00"));
        assertThat(result.triggered()).isTrue();
        assertThat(result.score()).isEqualTo(30);
    }

    @Test
    void shouldReportDisabledWhenConfigSaysSo() {
        RuleConfigurationProvider provider =
                () ->
                        List.of(
                                new RuleConfiguration(
                                        "FOREIGN_COUNTRY", false, 20, Map.of()));
        ForeignCountryRule disabledRule =
                new ForeignCountryRule(provider, "ZA", new BigDecimal("1000.00"), 20);

        assertThat(disabledRule.isEnabled()).isFalse();
    }

    private TransactionContext context(String countryCode, String amount) {
        TransactionEvent tx =
                new TransactionEvent(
                        UUID.randomUUID(),
                        "CUST001",
                        new BigDecimal(amount),
                        "MERCH001",
                        "Test Merchant",
                        TransactionCategory.ONLINE_PURCHASE,
                        "ZAR",
                        countryCode,
                        Instant.parse("2024-01-01T10:00:00Z"));

        return new TransactionContext(tx, 0);
    }
}
