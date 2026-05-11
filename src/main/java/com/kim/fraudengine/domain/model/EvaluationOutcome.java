package com.kim.fraudengine.domain.model;

import java.util.List;

public record EvaluationOutcome(
        List<RuleResult> allResults,
        List<RuleResult> triggeredResults,
        int totalScore,
        boolean thresholdExceeded) {

    public EvaluationOutcome {
        allResults = List.copyOf(allResults);
        triggeredResults = List.copyOf(triggeredResults);
    }
}
