package com.kim.fraudengine.domain.model;

public record RuleMessage(String ruleName, String reason) {

    public static RuleMessage pass(String ruleName) {
        return new RuleMessage(ruleName, null);
    }

    public static RuleMessage flag(String ruleName, String reason) {
        return new RuleMessage(ruleName, reason);
    }
}
