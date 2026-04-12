package com.kim.fraudengine.adapter.rest;

import com.kim.fraudengine.adapter.rest.dto.AlertResponse;
import com.kim.fraudengine.adapter.rest.exception.AlertNotFoundException;
import com.kim.fraudengine.adapter.rest.exception.InvalidFilterException;
import com.kim.fraudengine.adapter.rest.mapper.AlertMapper;
import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.port.inbound.GetAlertsUseCase;
import com.kim.fraudengine.infrastructure.logging.AuditLog;
import com.kim.fraudengine.infrastructure.security.CustomerAccessEvaluator;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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


@Tag(name = "Alerts", description = "Query fraud alerts. Requires authority: alerts:read (own) or alerts:read:all (global)")
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final GetAlertsUseCase getAlertsUseCase;
    private final CustomerAccessEvaluator customerAccessEvaluator;

    public AlertController(
            GetAlertsUseCase getAlertsUseCase,
            CustomerAccessEvaluator customerAccessEvaluator) {
        this.getAlertsUseCase = getAlertsUseCase;
        this.customerAccessEvaluator = customerAccessEvaluator;
    }

    @SuppressFBWarnings(value = "SPRING_ENDPOINT", justification = "Intentional secured REST endpoint")
    @Operation(summary = "Get alert by ID",
               description = "Returns the alert if it belongs to the authenticated customer, or 404 if not found or not accessible.")
    @ApiResponse(responseCode = "200", description = "Alert found",
                 content = @Content(schema = @Schema(implementation = AlertResponse.class)))
    @ApiResponse(responseCode = "404", description = "Alert not found")
    @PreAuthorize("hasAuthority('alerts:read')")
    @GetMapping("/{id}")
    public AlertResponse getById(@PathVariable UUID id,
                                 Authentication authentication) {
        AlertResponse response = getAlertsUseCase.getById(id)
                .map(AlertMapper::toResponse)
                .orElseThrow(() -> new AlertNotFoundException(id));
        if (!customerAccessEvaluator.canRead(response.customerId(), authentication)) {
            throw new AlertNotFoundException(id);
        }
        AuditLog.record("ALERT_VIEWED", auditDetails(authentication, id)
                .append("customerId", response.customerId())
                .append("severity", response.highestSeverity().name())
                .append("status", response.status().name())
                .build());
        return response;
    }

    @SuppressFBWarnings(value = "SPRING_ENDPOINT", justification = "Intentional secured REST endpoint")
    @Operation(summary = "Get alerts by customer ID",
               description = "Returns all alerts for the given customer. Access restricted to the customer's own account, or users with alerts:read:all.")
    @ApiResponse(responseCode = "200", description = "Alerts returned (may be empty)")
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

    @SuppressFBWarnings(value = "SPRING_ENDPOINT", justification = "Intentional secured REST endpoint")
    @Operation(summary = "Filter alerts globally",
               description = "Returns all alerts matching the given filter. Exactly one of `status` or `severity` is required. Requires alerts:read:all authority.")
    @ApiResponse(responseCode = "200", description = "Alerts returned")
    @ApiResponse(responseCode = "400", description = "No filter parameter provided")
    @PreAuthorize("hasAuthority('alerts:read:all')")
    @GetMapping
    public List<AlertResponse> getByFilter(
            @Parameter(description = "Filter by alert status") @RequestParam(required = false) AlertStatus status,
            @Parameter(description = "Filter by severity") @RequestParam(required = false) Severity severity,
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
