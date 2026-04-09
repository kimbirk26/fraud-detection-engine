package com.kim.fraudengine.infrastructure.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Structured audit logger for REST-layer access events.
 * Writes to a dedicated logger so audit records can be routed
 * independently of application logs.
 */
public final class AuditLog {

    private static final Logger log = LoggerFactory.getLogger("com.kim.fraud.audit");

    private AuditLog() {
    }

    public static void record(String event, Map<String, Object> details) {
        StringBuilder sb = new StringBuilder("event=").append(event);
        details.forEach((k, v) -> sb.append(' ').append(k).append('=').append(v));
        log.info(sb.toString());
    }
}
