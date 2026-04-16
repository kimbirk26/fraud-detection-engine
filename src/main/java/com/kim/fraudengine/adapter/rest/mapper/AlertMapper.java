package com.kim.fraudengine.adapter.rest.mapper;

import com.kim.fraudengine.adapter.rest.dto.AlertResponse;
import com.kim.fraudengine.adapter.rest.dto.AlertResponse.RuleResultResponse;
import com.kim.fraudengine.adapter.rest.dto.AlertStatusResponse;
import com.kim.fraudengine.adapter.rest.dto.SeverityResponse;
import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.FraudAlert;
import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import java.util.List;

public final class AlertMapper {

    private AlertMapper() {}

    public static AlertResponse toResponse(FraudAlert alert) {
        List<RuleResultResponse> rules =
                alert.triggeredRules().stream().map(AlertMapper::toRuleResponse).toList();
        return new AlertResponse(
                alert.id(),
                alert.transactionId(),
                alert.customerId(),
                rules,
                toResponse(alert.highestSeverity()),
                toResponse(alert.status()),
                alert.createdAt());
    }

    private static RuleResultResponse toRuleResponse(RuleResult rule) {
        return new RuleResultResponse(rule.ruleName(), toResponse(rule.severity()), rule.reason());
    }

    private static SeverityResponse toResponse(Severity severity) {
        return switch (severity) {
            case NONE -> SeverityResponse.NONE;
            case LOW -> SeverityResponse.LOW;
            case MEDIUM -> SeverityResponse.MEDIUM;
            case HIGH -> SeverityResponse.HIGH;
        };
    }

    private static AlertStatusResponse toResponse(AlertStatus status) {
        return switch (status) {
            case OPEN -> AlertStatusResponse.OPEN;
            case UNDER_REVIEW -> AlertStatusResponse.UNDER_REVIEW;
            case RESOLVED -> AlertStatusResponse.RESOLVED;
            case FALSE_POSITIVE -> AlertStatusResponse.FALSE_POSITIVE;
        };
    }
}
