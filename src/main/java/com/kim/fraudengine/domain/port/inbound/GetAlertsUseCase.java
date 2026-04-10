package com.kim.fraudengine.domain.port.inbound;

import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.FraudAlert;
import com.kim.fraudengine.domain.model.Severity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port: querying fraud alerts.
 */
public interface GetAlertsUseCase {
    List<FraudAlert> getByCustomerId(String customerId);

    List<FraudAlert> getByStatus(AlertStatus status);

    List<FraudAlert> getBySeverity(Severity severity);

    Optional<FraudAlert> getById(UUID id);
}
