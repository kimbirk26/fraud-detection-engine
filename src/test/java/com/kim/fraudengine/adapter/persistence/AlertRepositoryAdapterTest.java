package com.kim.fraudengine.adapter.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.fraudengine.adapter.persistence.entity.AlertEntity;
import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.FraudAlert;
import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionCategory;
import com.kim.fraudengine.domain.model.TransactionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRepositoryAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @Mock
    private AlertJpaRepository jpaRepository;
    private AlertRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AlertRepositoryAdapter(jpaRepository, objectMapper);
    }

    @Test
    void shouldSerializeTriggeredRulesWhenSavingAlert() throws IOException {
        FraudAlert alert = FraudAlert.from(transaction(), List.of(RuleResult.flag("AMOUNT_THRESHOLD", Severity.HIGH, "Amount exceeds threshold")));

        when(jpaRepository.saveAndFlush(any(AlertEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FraudAlert saved = adapter.save(alert);

        ArgumentCaptor<AlertEntity> entityCaptor = ArgumentCaptor.forClass(AlertEntity.class);
        verify(jpaRepository).saveAndFlush(entityCaptor.capture());
        AlertEntity entity = entityCaptor.getValue();

        assertThat(entity.getTransactionId()).isEqualTo(alert.transactionId());
        assertThat(entity.getCustomerId()).isEqualTo(alert.customerId());
        assertThat(objectMapper.readTree(entity.getTriggeredRulesJson()).toString()).contains("AMOUNT_THRESHOLD").contains("Amount exceeds threshold");
        assertThat(saved.triggeredRules()).containsExactlyElementsOf(alert.triggeredRules());
    }

    @Test
    void shouldDeserializeTriggeredRulesWhenFindingByTransactionId() throws IOException {
        FraudAlert alert = FraudAlert.from(transaction(), List.of(RuleResult.flag("BLACKLIST_MATCH", Severity.HIGH, "Merchant is blacklisted")));
        AlertEntity entity = entity(alert);

        when(jpaRepository.findByTransactionId(alert.transactionId())).thenReturn(Optional.of(entity));

        Optional<FraudAlert> result = adapter.findByTransactionId(alert.transactionId());

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().triggeredRules()).containsExactlyElementsOf(alert.triggeredRules());
        assertThat(result.orElseThrow().highestSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void shouldMapAlertsWhenFindingByCustomerId() throws IOException {
        FraudAlert firstAlert = FraudAlert.from(transaction(), List.of(RuleResult.flag("AMOUNT_THRESHOLD", Severity.MEDIUM, "Amount exceeds threshold")));
        FraudAlert secondAlert = FraudAlert.from(secondTransaction(), List.of(RuleResult.flag("BLACKLIST_MATCH", Severity.HIGH, "Merchant is blacklisted")));

        when(jpaRepository.findByCustomerId("CUST001")).thenReturn(List.of(entity(firstAlert), entity(secondAlert)));

        List<FraudAlert> result = adapter.findByCustomerId("CUST001");

        assertThat(result).extracting(FraudAlert::transactionId).containsExactly(firstAlert.transactionId(), secondAlert.transactionId());
        assertThat(result).extracting(FraudAlert::highestSeverity).containsExactly(Severity.MEDIUM, Severity.HIGH);
    }

    @Test
    void shouldMapAlertsWhenFindingByStatus() throws IOException {
        FraudAlert alert = FraudAlert.from(transaction(), List.of(RuleResult.flag("FOREIGN_COUNTRY", Severity.MEDIUM, "Foreign transaction")));

        when(jpaRepository.findByStatus(AlertStatus.OPEN)).thenReturn(List.of(entity(alert)));

        List<FraudAlert> result = adapter.findByStatus(AlertStatus.OPEN);

        assertThat(result).singleElement().satisfies(found -> {
            assertThat(found.status()).isEqualTo(AlertStatus.OPEN);
            assertThat(found.triggeredRules()).containsExactlyElementsOf(alert.triggeredRules());
        });
    }

    @Test
    void shouldMapAlertsWhenFindingBySeverity() throws IOException {
        FraudAlert alert = FraudAlert.from(secondTransaction(), List.of(RuleResult.flag("BLACKLIST_MATCH", Severity.HIGH, "Merchant is blacklisted")));

        when(jpaRepository.findByHighestSeverity(Severity.HIGH)).thenReturn(List.of(entity(alert)));

        List<FraudAlert> result = adapter.findBySeverity(Severity.HIGH);

        assertThat(result).singleElement().satisfies(found -> {
            assertThat(found.highestSeverity()).isEqualTo(Severity.HIGH);
            assertThat(found.transactionId()).isEqualTo(alert.transactionId());
        });
    }

    private AlertEntity entity(FraudAlert alert) throws IOException {
        return new AlertEntity(alert.id(), alert.transactionId(), alert.customerId(), objectMapper.writeValueAsString(alert.triggeredRules()), alert.highestSeverity(), alert.status(), alert.createdAt());
    }

    private TransactionEvent transaction() {
        return new TransactionEvent(UUID.fromString("11111111-1111-1111-1111-111111111111"), "CUST001", new BigDecimal("15000.00"), "MERCH001", "Test Merchant", TransactionCategory.ONLINE_PURCHASE, "ZAR", "ZA", Instant.parse("2024-01-01T10:00:00Z"));
    }

    private TransactionEvent secondTransaction() {
        return new TransactionEvent(UUID.fromString("22222222-2222-2222-2222-222222222222"), "CUST001", new BigDecimal("250.00"), "MERCH_BAD_001", "Blocked Merchant", TransactionCategory.ONLINE_PURCHASE, "ZAR", "ZA", Instant.parse("2024-01-01T10:05:00Z"));
    }
}
