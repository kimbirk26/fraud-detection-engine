package com.kim.fraudengine.domain.port.outbound;

import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.FraudAlert;
import com.kim.fraudengine.domain.model.Severity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port: persistence contract the domain defines.
 */
public interface AlertRepository {
    FraudAlert save(FraudAlert alert);

    Optional<FraudAlert> findById(UUID id);

    Optional<FraudAlert> findByTransactionId(UUID transactionId);

    List<FraudAlert> findByCustomerId(String customerId);

    List<FraudAlert> findByStatus(AlertStatus status);

    List<FraudAlert> findBySeverity(Severity severity);
}
