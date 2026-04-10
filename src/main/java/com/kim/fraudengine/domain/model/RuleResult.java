package com.kim.fraudengine.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record RuleResult(boolean triggered, Severity severity, RuleMessage message) {

    public static RuleResult pass(String ruleName) {
        return new RuleResult(false, Severity.NONE, RuleMessage.pass(ruleName));
    }

    public static RuleResult flag(String ruleName, Severity severity, String reason) {
        return new RuleResult(true, severity, RuleMessage.flag(ruleName, reason));
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
