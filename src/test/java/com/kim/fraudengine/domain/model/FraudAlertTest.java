package com.kim.fraudengine.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FraudAlertTest {

    @Test
    void constructor_defensively_copies_triggered_rules() {
        List<RuleResult> triggeredRules = new ArrayList<>();
        triggeredRules.add(RuleResult.flag("BLACKLIST_MATCH", Severity.HIGH, "blacklisted"));

        FraudAlert alert = new FraudAlert(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "CUST001",
                triggeredRules,
                Severity.HIGH,
                AlertStatus.OPEN,
                Instant.parse("2026-04-11T00:00:00Z"));

        triggeredRules.clear();

        assertThat(alert.triggeredRules()).hasSize(1);
        assertThatThrownBy(() -> alert.triggeredRules().add(RuleResult.pass("SAFE")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
