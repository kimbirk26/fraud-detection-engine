package com.kim.fraudengine.domain.model;

import java.util.Map;

/**
 * Represents the configuration for a single fraud detection rule. Used by the
 * {@code RuleConfigurationProvider} to supply rule parameters at runtime.
 *
 * @param ruleName unique identifier matching {@code FraudRule.ruleName()}
 * @param enabled whether the rule should participate in evaluation
 * @param score the score contributed when this rule triggers
 * @param parameters rule-specific parameters (thresholds, lists, etc.)
 */
public record RuleConfiguration(
        String ruleName,
        boolean enabled,
        int score,
        Map<String, String> parameters) {

    public RuleConfiguration {
        parameters = Map.copyOf(parameters);
    }
}
