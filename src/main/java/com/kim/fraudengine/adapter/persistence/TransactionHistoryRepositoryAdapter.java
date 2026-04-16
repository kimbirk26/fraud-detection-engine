package com.kim.fraudengine.adapter.persistence;

import com.kim.fraudengine.adapter.persistence.entity.ProcessedTransactionEntity;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.outbound.TransactionHistoryRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransactionHistoryRepositoryAdapter implements TransactionHistoryRepository {

    private final ProcessedTransactionJpaRepository jpaRepository;
    private final JdbcTemplate jdbcTemplate;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Spring-managed singleton - effectively immutable after context initialization")
    public TransactionHistoryRepositoryAdapter(
            ProcessedTransactionJpaRepository jpaRepository, JdbcTemplate jdbcTemplate) {
        this.jpaRepository = jpaRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void lockCustomer(String customerId) {
        String normalizedCustomerId =
                Objects.requireNonNull(customerId, "customerId must not be null");
        jdbcTemplate.execute(
                (ConnectionCallback<Void>)
                        connection -> {
                            try (var statement =
                                    connection.prepareStatement(
                                            "select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                                statement.setString(1, normalizedCustomerId);
                                statement.execute();
                                return null;
                            }
                        });
    }

    @Override
    public void save(TransactionEvent transactionEvent) {
        jpaRepository.saveAndFlush(
                new ProcessedTransactionEntity(
                        transactionEvent.id(),
                        transactionEvent.customerId(),
                        transactionEvent.amount(),
                        transactionEvent.merchantId(),
                        transactionEvent.merchantName(),
                        transactionEvent.category(),
                        transactionEvent.currency(),
                        transactionEvent.countryCode(),
                        transactionEvent.timestamp()));
    }

    @Override
    public boolean existsByTransactionId(UUID transactionId) {
        return jpaRepository.existsById(transactionId);
    }

    @Override
    public long countByCustomerIdSince(String customerId, Instant windowStart) {
        return jpaRepository.countByCustomerIdAndOccurredAtGreaterThanEqual(
                customerId, windowStart);
    }
}
