package com.kim.fraudengine.adapter.persistence;

import com.kim.fraudengine.adapter.persistence.entity.AlertEntity;
import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.Severity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertJpaRepository extends JpaRepository<AlertEntity, UUID> {
    Optional<AlertEntity> findByTransactionId(UUID transactionId);

    List<AlertEntity> findByCustomerId(String customerId);

    List<AlertEntity> findByStatus(AlertStatus status);

    List<AlertEntity> findByHighestSeverity(Severity severity);
}
