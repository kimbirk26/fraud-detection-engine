package com.kim.fraudengine.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveLogValueSanitizerTest {

    @Test
    void sanitizePathRedactsUuidAndIdentifierSegments() {
        String sanitized =
                SensitiveLogValueSanitizer.sanitizePath(
                        "/customers/customer_ab12/orders/550e8400-e29b-41d4-a716-446655440000/details");

        assertThat(sanitized).isEqualTo("/customers/{id}/orders/{id}/details");
    }

    @Test
    void sanitizePathRedactsCaseInsensitiveIdentifierSegmentsWithSuffixes() {
        String sanitized =
                SensitiveLogValueSanitizer.sanitizePath("/users/CUSTOMER-AB12_extra/profile");

        assertThat(sanitized).isEqualTo("/users/{id}/profile");
    }

    @Test
    void sanitizePathLeavesSegmentsThatDoNotMatchIdentifierShape() {
        String sanitized =
                SensitiveLogValueSanitizer.sanitizePath("/reports/acct_team_ab12/q1_summary_2024");

        assertThat(sanitized).isEqualTo("/reports/acct_team_ab12/q1_summary_2024");
    }
}
