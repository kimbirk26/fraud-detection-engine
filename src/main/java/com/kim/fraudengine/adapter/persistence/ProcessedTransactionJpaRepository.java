package com.kim.fraudengine.adapter.persistence;

import com.kim.fraudengine.adapter.persistence.entity.ProcessedTransactionEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedTransactionJpaRepository
        extends JpaRepository<ProcessedTransactionEntity, UUID> {

    long countByCustomerIdAndOccurredAtGreaterThanEqual(String customerId, Instant occurredAt);

    @Query("SELECT p.customerId FROM ProcessedTransactionEntity p WHERE p.transactionId = :transactionId")
    Optional<String> findCustomerIdByTransactionId(@Param("transactionId") UUID transactionId);
}
