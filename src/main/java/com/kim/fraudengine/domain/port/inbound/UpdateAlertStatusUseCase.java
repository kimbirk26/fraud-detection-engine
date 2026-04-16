package com.kim.fraudengine.domain.port.inbound;

import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.FraudAlert;
import java.util.Optional;
import java.util.UUID;

/** Inbound port: update the status of an existing fraud alert. */
public interface UpdateAlertStatusUseCase {
    Optional<FraudAlert> updateStatus(UUID alertId, AlertStatus newStatus);
}
