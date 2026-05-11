package com.kim.fraudengine.adapter.persistence;

import com.kim.fraudengine.adapter.persistence.entity.AlertEntity;
import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertJpaRepository extends JpaRepository<AlertEntity, UUID> {
    Optional<AlertEntity> findByTransactionId(UUID transactionId);

    List<AlertEntity> findByCustomerId(String customerId);

    List<AlertEntity> findByStatus(AlertStatus status);

    List<AlertEntity> findByHighestSeverity(Severity severity);

    @Query("SELECT a FROM AlertEntity a WHERE a.customerId = :customerId "
            + "AND a.status = 'OPEN' AND a.createdAt >= :since "
            + "ORDER BY a.createdAt DESC LIMIT 1")
    Optional<AlertEntity> findLatestOpenByCustomerId(
            @Param("customerId") String customerId,
            @Param("since") Instant since);
}
