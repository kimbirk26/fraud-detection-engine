package com.kim.fraudengine.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.FraudAlert;
import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionCategory;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.outbound.AlertRepository;
import com.kim.fraudengine.domain.port.outbound.TransactionHistoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs locally when Docker is available and verifies Flyway + JPA + adapter wiring against a real
 * Postgres instance.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ImportAutoConfiguration
class PostgresPersistenceIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private AlertRepository alertRepository;
    @Autowired private TransactionHistoryRepository transactionHistoryRepository;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Test
    void shouldPersistAndQueryAlertsAgainstPostgres() {
        TransactionEvent transaction =
                transaction(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        "CUST-POSTGRES-001",
                        new BigDecimal("2500.00"),
                        Instant.parse("2024-01-01T10:00:00Z"));
        FraudAlert alert =
                FraudAlert.from(
                        transaction,
                        List.of(
                                RuleResult.flag(
                                        "FOREIGN_COUNTRY",
                                        Severity.MEDIUM,
                                        "Foreign transaction")));

        FraudAlert saved = alertRepository.save(alert);

        assertThat(alertRepository.findById(saved.id())).contains(saved);
        assertThat(alertRepository.findByTransactionId(saved.transactionId())).contains(saved);
        assertThat(alertRepository.findByCustomerId(saved.customerId()))
                .extracting(FraudAlert::transactionId)
                .contains(saved.transactionId());
        assertThat(alertRepository.findByStatus(AlertStatus.OPEN))
                .extracting(FraudAlert::id)
                .contains(saved.id());
        assertThat(alertRepository.findBySeverity(Severity.MEDIUM))
                .extracting(FraudAlert::id)
                .contains(saved.id());
    }

    @Test
    void shouldPersistAndQueryTransactionHistoryAgainstPostgres() {
        TransactionEvent first =
                transaction(
                        UUID.fromString("44444444-4444-4444-4444-444444444444"),
                        "CUST-POSTGRES-002",
                        new BigDecimal("100.00"),
                        Instant.parse("2024-01-01T10:00:00Z"));
        TransactionEvent second =
                transaction(
                        UUID.fromString("55555555-5555-5555-5555-555555555555"),
                        "CUST-POSTGRES-002",
                        new BigDecimal("150.00"),
                        Instant.parse("2024-01-01T10:04:00Z"));

        transactionHistoryRepository.save(first);
        transactionHistoryRepository.save(second);
        transactionHistoryRepository.lockCustomer("CUST-POSTGRES-002");

        Optional<FraudAlert> noAlert = alertRepository.findByTransactionId(first.id());

        assertThat(transactionHistoryRepository.existsByTransactionId(first.id())).isTrue();
        assertThat(
                        transactionHistoryRepository.countByCustomerIdSince(
                                "CUST-POSTGRES-002", first.timestamp()))
                .isEqualTo(2L);
        assertThat(
                        transactionHistoryRepository.countByCustomerIdSince(
                                "CUST-POSTGRES-002", Instant.parse("2024-01-01T09:59:00Z")))
                .isEqualTo(2L);
        assertThat(noAlert).isEmpty();
    }

    private TransactionEvent transaction(
            UUID id, String customerId, BigDecimal amount, Instant timestamp) {
        return new TransactionEvent(
                id,
                customerId,
                amount,
                "MERCH001",
                "Test Merchant",
                TransactionCategory.ONLINE_PURCHASE,
                "ZAR",
                "ZA",
                timestamp);
    }
}
