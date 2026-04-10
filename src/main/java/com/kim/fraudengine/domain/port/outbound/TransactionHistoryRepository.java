package com.kim.fraudengine.domain.port.outbound;

import com.kim.fraudengine.domain.model.TransactionEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound port for querying and storing processed transaction history. Velocity-based rules depend
 * on both clean transactions and flagged ones.
 */
public interface TransactionHistoryRepository {
    void lockCustomer(String customerId);

    void save(TransactionEvent transactionEvent);

    boolean existsByTransactionId(UUID transactionId);

    long countByCustomerIdSince(String customerId, Instant windowStart);
}
