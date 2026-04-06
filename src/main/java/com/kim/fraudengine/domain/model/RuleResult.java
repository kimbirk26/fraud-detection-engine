package com.kim.fraudengine.domain.model;


public record RuleResult(
        boolean triggered,
        String ruleName,
        Severity severity,
        String reason
) {
    public static RuleResult pass(String ruleName) {
        return new RuleResult(false, ruleName, null, null);
    }

    public static RuleResult flag(String ruleName, Severity severity, String reason) {
        return new RuleResult(true, ruleName, severity, reason);
    }
}
