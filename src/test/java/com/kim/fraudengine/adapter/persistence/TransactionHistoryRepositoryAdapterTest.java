package com.kim.fraudengine.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kim.fraudengine.adapter.persistence.entity.ProcessedTransactionEntity;
import com.kim.fraudengine.domain.model.TransactionCategory;
import com.kim.fraudengine.domain.model.TransactionEvent;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class TransactionHistoryRepositoryAdapterTest {

    @Mock private ProcessedTransactionJpaRepository jpaRepository;

    @Mock private JdbcTemplate jdbcTemplate;

    private TransactionHistoryRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TransactionHistoryRepositoryAdapter(jpaRepository, jdbcTemplate);
    }

    @Test
    void shouldPersistProcessedTransactionFields() {
        TransactionEvent transaction = transaction();

        adapter.save(transaction);

        ArgumentCaptor<ProcessedTransactionEntity> entityCaptor =
                ArgumentCaptor.forClass(ProcessedTransactionEntity.class);
        verify(jpaRepository).saveAndFlush(entityCaptor.capture());
        ProcessedTransactionEntity entity = entityCaptor.getValue();

        assertThat(entity)
                .extracting(
                        "transactionId",
                        "customerId",
                        "merchantId",
                        "merchantName",
                        "currency",
                        "countryCode")
                .containsExactly(
                        transaction.id(),
                        transaction.customerId(),
                        transaction.merchantId(),
                        transaction.merchantName(),
                        transaction.currency(),
                        transaction.countryCode());
    }

    @Test
    void shouldDelegateExistenceAndWindowCounts() {
        UUID transactionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant windowStart = Instant.parse("2024-01-01T09:55:00Z");

        when(jpaRepository.existsById(transactionId)).thenReturn(true);
        when(jpaRepository.countByCustomerIdAndOccurredAtGreaterThanEqual("CUST001", windowStart))
                .thenReturn(4L);

        assertThat(adapter.existsByTransactionId(transactionId)).isTrue();
        assertThat(adapter.countByCustomerIdSince("CUST001", windowStart)).isEqualTo(4L);
    }

    @Test
    void shouldAcquireCustomerLockUsingAdvisoryLockQuery() throws Exception {
        ArgumentCaptor<ConnectionCallback<Void>> callbackCaptor =
                ArgumentCaptor.forClass(ConnectionCallback.class);
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenReturn(null);

        adapter.lockCustomer("CUST001");

        verify(jdbcTemplate).execute(callbackCaptor.capture());

        Connection connection = org.mockito.Mockito.mock(Connection.class);
        PreparedStatement statement = org.mockito.Mockito.mock(PreparedStatement.class);
        when(connection.prepareStatement(any(String.class))).thenReturn(statement);

        callbackCaptor.getValue().doInConnection(connection);

        verify(connection).prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))");
        verify(statement).setString(1, "CUST001");
        verify(statement).execute();
        verify(statement).close();
    }

    private TransactionEvent transaction() {
        return new TransactionEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "CUST001",
                new BigDecimal("1500.00"),
                "MERCH001",
                "Test Merchant",
                TransactionCategory.ONLINE_PURCHASE,
                "ZAR",
                "ZA",
                Instant.parse("2024-01-01T10:00:00Z"));
    }
}
