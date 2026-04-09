package com.kim.fraudengine.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.kim.fraudengine.domain.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BlacklistRuleTest {

  private final BlacklistRule rule = new BlacklistRule(Set.of("MERCH_BAD_001", "MERCH_BAD_002"));

  @Test
  void shouldPassForCleanMerchant() {
    TransactionEvent tx = transaction("MERCH_LEGIT");
    RuleResult result = rule.evaluate(context(tx));

    assertThat(result.triggered()).isFalse();
  }

  @Test
  void shouldFlagBlacklistedMerchant() {
    TransactionEvent tx = transaction("MERCH_BAD_001");
    RuleResult result = rule.evaluate(context(tx));

    assertThat(result.triggered()).isTrue();
    assertThat(result.severity()).isEqualTo(Severity.HIGH);
    assertThat(result.reason()).contains("MERCH_BAD_001");
  }

  private TransactionContext context(TransactionEvent tx) {
    return new TransactionContext(tx, 0);
  }

  private TransactionEvent transaction(String merchantId) {
    return new TransactionEvent(
        UUID.randomUUID(),
        "CUST001",
        BigDecimal.TEN,
        merchantId,
        "Some Merchant",
        TransactionCategory.ONLINE_PURCHASE,
        "ZAR",
        "ZA",
        Instant.parse("2024-01-01T10:00:00Z"));
  }
}
