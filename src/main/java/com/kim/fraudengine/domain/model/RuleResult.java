package com.kim.fraudengine.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record RuleResult(boolean triggered, Severity severity, RuleMessage message, int score) {

    public static RuleResult pass(String ruleName) {
        return new RuleResult(false, Severity.NONE, RuleMessage.pass(ruleName), 0);
    }

    public static RuleResult flag(String ruleName, Severity severity, String reason, int score) {
        return new RuleResult(true, severity, RuleMessage.flag(ruleName, reason), score);
    }

    @JsonIgnore
    public boolean isTriggered() {
        return triggered;
    }

    @JsonIgnore
    public boolean isHighSeverity() {
        return severity == Severity.HIGH;
    }

    public String ruleName() {
        return message.ruleName();
    }

    public String reason() {
        return message.reason();
    }
}
