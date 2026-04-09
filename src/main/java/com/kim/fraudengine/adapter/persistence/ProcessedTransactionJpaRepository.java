package com.kim.fraudengine.adapter.persistence;

import com.kim.fraudengine.adapter.persistence.entity.ProcessedTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedTransactionJpaRepository
        extends JpaRepository<ProcessedTransactionEntity, UUID> {

    long countByCustomerIdAndOccurredAtGreaterThanEqual(String customerId, Instant occurredAt);
}
