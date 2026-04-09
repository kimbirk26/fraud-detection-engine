package com.kim.fraudengine.adapter.rest.exception;

import java.util.UUID;

public class AlertNotFoundException extends RuntimeException {

    public AlertNotFoundException(UUID id) {
        super("Alert not found: " + id);
    }
}
