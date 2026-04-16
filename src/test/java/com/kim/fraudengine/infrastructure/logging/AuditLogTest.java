package com.kim.fraudengine.infrastructure.logging;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogTest {

    @Test
    void formatAuditLineSanitizesSensitiveDetailsWithoutLeakingRawValues() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("requestedBy", "customer_ab12");
        details.put("path", "/api/v1/alerts/customer_ab12");

        String line = AuditLog.formatAuditLine("ALERT_VIEWED", details);

        assertThat(line).contains("event=ALERT_VIEWED")
                .contains("requestedBy=cu***12")
                .contains("path=/api/v1/alerts/{id}")
                .doesNotContain("requestedBy=customer_ab12")
                .doesNotContain("path=/api/v1/alerts/customer_ab12");
    }

    @Test
    void formatAuditLineHandlesNullDetails() {
        assertThat(AuditLog.formatAuditLine("ALERT_VIEWED", null)).isEqualTo("event=ALERT_VIEWED");
    }
}
