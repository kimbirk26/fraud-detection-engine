package com.kim.fraudengine.adapter.rest;

import com.kim.fraudengine.adapter.rest.dto.AlertResponse;
import com.kim.fraudengine.adapter.rest.exception.AlertNotFoundException;
import com.kim.fraudengine.adapter.rest.exception.InvalidFilterException;
import com.kim.fraudengine.adapter.rest.mapper.AlertMapper;
import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.port.inbound.GetAlertsUseCase;
import com.kim.fraudengine.infrastructure.logging.AuditLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final GetAlertsUseCase getAlertsUseCase;

    public AlertController(GetAlertsUseCase getAlertsUseCase) {
        this.getAlertsUseCase = getAlertsUseCase;
    }

    @PreAuthorize("hasAuthority('alerts:read')")
    @GetMapping("/{id}")
    public AlertResponse getById(@PathVariable UUID id,
                                 Authentication authentication) {
        AlertResponse response = getAlertsUseCase.getById(id)
                .map(AlertMapper::toResponse)
                .orElseThrow(() -> new AlertNotFoundException(id));
        AuditLog.record("ALERT_VIEWED", auditDetails(authentication, id)
                .append("customerId", response.customerId())
                .append("severity", response.highestSeverity().name())
                .append("status", response.status().name())
                .build());
        return response;
    }

    @PreAuthorize("hasAuthority('alerts:read') and @customerAccess.canRead(#customerId, authentication)")
    @GetMapping("/customer/{customerId}")
    public List<AlertResponse> getByCustomer(@PathVariable String customerId,
                                             Authentication authentication) {
        List<AlertResponse> alerts = getAlertsUseCase.getByCustomerId(customerId)
                .stream()
                .map(AlertMapper::toResponse)
                .toList();
        AuditLog.record("ALERT_LIST_VIEWED", auditDetails(authentication, null)
                .append("requestedCustomerId", customerId)
                .append("resultCount", alerts.size())
                .build());
        return alerts;
    }

    @PreAuthorize("hasAuthority('alerts:read')")
    @GetMapping
    public List<AlertResponse> getByFilter(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) Severity severity,
            Authentication authentication) {

        if (status != null) {
            List<AlertResponse> alerts = getAlertsUseCase.getByStatus(status)
                    .stream()
                    .map(AlertMapper::toResponse)
                    .toList();
            AuditLog.record("ALERT_FILTER_VIEWED", auditDetails(authentication, null)
                    .append("filterType", "status")
                    .append("filterValue", status.name())
                    .append("resultCount", alerts.size())
                    .build());
            return alerts;
        }
        if (severity != null) {
            List<AlertResponse> alerts = getAlertsUseCase.getBySeverity(severity)
                    .stream()
                    .map(AlertMapper::toResponse)
                    .toList();
            AuditLog.record("ALERT_FILTER_VIEWED", auditDetails(authentication, null)
                    .append("filterType", "severity")
                    .append("filterValue", severity.name())
                    .append("resultCount", alerts.size())
                    .build());
            return alerts;
        }

        throw new InvalidFilterException(
                "At least one filter parameter is required: status or severity. " +
                "Example: GET /api/v1/alerts?status=OPEN");
    }

    private AuditDetailsBuilder auditDetails(Authentication authentication, UUID alertId) {
        AuditDetailsBuilder builder = new AuditDetailsBuilder();
        if (authentication != null && authentication.getName() != null) {
            builder.append("requestedBy", authentication.getName());
        }
        if (alertId != null) {
            builder.append("alertId", alertId);
        }
        return builder;
    }

    private static final class AuditDetailsBuilder {
        private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

        private AuditDetailsBuilder append(String key, Object value) {
            if (value != null) {
                values.put(key, value);
            }
            return this;
        }

        private LinkedHashMap<String, Object> build() {
            return values;
        }
    }
}
