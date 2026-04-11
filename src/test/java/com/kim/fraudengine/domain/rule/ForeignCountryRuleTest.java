package com.kim.fraudengine.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionCategory;
import com.kim.fraudengine.domain.model.TransactionContext;
import com.kim.fraudengine.domain.model.TransactionEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ForeignCountryRuleTest {

  private ForeignCountryRule rule;

  @BeforeEach
  void setUp() {
    rule = new ForeignCountryRule("ZA", new BigDecimal("1000.00"));
  }

  @Test
  void shouldPassForHomeCountryTransaction() {
    RuleResult result = rule.evaluate(context("ZA", "2500.00"));

    assertThat(result.triggered()).isFalse();
  }

  @Test
  void shouldPassWhenAmountIsBelowThreshold() {
    RuleResult result = rule.evaluate(context("FR", "500.00"));
    assertThat(result.triggered()).isFalse();
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
      ForeignCountryRule localeSafeRule = new ForeignCountryRule("in", new BigDecimal("1000.00"));

      RuleResult result = localeSafeRule.evaluate(context("IN", "2500.00"));

      assertThat(result.triggered()).isFalse();
    } finally {
      Locale.setDefault(previous);
    }
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
