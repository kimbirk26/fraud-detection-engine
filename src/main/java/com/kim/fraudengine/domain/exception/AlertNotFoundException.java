package com.kim.fraudengine.domain.exception;

import java.util.UUID;

public class AlertNotFoundException extends RuntimeException {

    private final UUID alertId;

    public AlertNotFoundException(UUID alertId) {
        super("Fraud alert not found: " + alertId);
        this.alertId = alertId;
    }

    public UUID getAlertId() {
        return alertId;
    }
}
