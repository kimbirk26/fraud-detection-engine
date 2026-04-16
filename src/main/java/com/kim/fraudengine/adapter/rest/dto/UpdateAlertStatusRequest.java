package com.kim.fraudengine.adapter.rest.dto;

import com.kim.fraudengine.domain.model.AlertStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAlertStatusRequest(
        @NotNull(message = "status is required") AlertStatus status) {}
