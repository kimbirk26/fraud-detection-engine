package com.kim.fraudengine.adapter.persistence;

import com.kim.fraudengine.adapter.persistence.entity.ProcessedTransactionEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedTransactionJpaRepository
        extends JpaRepository<ProcessedTransactionEntity, UUID> {

    long countByCustomerIdAndOccurredAtGreaterThanEqual(String customerId, Instant occurredAt);
}
