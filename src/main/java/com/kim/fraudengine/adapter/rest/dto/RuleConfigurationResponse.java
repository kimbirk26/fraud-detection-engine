package com.kim.fraudengine.adapter.rest.dto;

import java.time.Instant;
import java.util.Map;

public record RuleConfigurationResponse(
        String ruleName, boolean enabled, int score, Map<String, String> parameters, Instant updatedAt) {

    public RuleConfigurationResponse {
        parameters = Map.copyOf(parameters);
    }
}
