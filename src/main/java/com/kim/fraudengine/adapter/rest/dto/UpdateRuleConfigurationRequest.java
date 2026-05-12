package com.kim.fraudengine.adapter.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Map;

public record UpdateRuleConfigurationRequest(
        @NotNull Boolean enabled,
        @PositiveOrZero int score,
        @NotNull Map<String, String> parameters) {

    public UpdateRuleConfigurationRequest {
        parameters = Map.copyOf(parameters);
    }
}
