package com.kim.fraudengine.application;

import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.FraudAlert;
import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionCategory;
import com.kim.fraudengine.domain.model.TransactionContext;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.outbound.AlertRepository;
import com.kim.fraudengine.domain.port.outbound.TransactionHistoryRepository;
import com.kim.fraudengine.domain.rule.RuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock
    private RuleEngine ruleEngine;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private TransactionHistoryRepository transactionHistoryRepository;

    private final TransactionOperations transactionOperations = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    };

    private FraudDetectionService service;

    @BeforeEach
    void setUp() {
        service =
                new FraudDetectionService(
                        ruleEngine,
                        alertRepository,
                        transactionHistoryRepository,
                        transactionOperations,
                        5);
    }

    @Test
    void shouldReturnEmptyAndSaveTransactionWhenNoRulesTrigger() {
        TransactionEvent transaction = transaction();
        Instant windowStart = transaction.timestamp().minusSeconds(300);

        when(transactionHistoryRepository.existsByTransactionId(transaction.id())).thenReturn(false);
        when(transactionHistoryRepository.countByCustomerIdSince(transaction.customerId(), windowStart))
                .thenReturn(2L);
        when(ruleEngine.evaluate(new TransactionContext(transaction, 2L))).thenReturn(List.of());

        Optional<FraudAlert> result = service.process(transaction);

        assertThat(result).isEmpty();
        verify(transactionHistoryRepository).lockCustomer(transaction.customerId());
        verify(transactionHistoryRepository)
                .countByCustomerIdSince(transaction.customerId(), windowStart);
        verify(ruleEngine).evaluate(new TransactionContext(transaction, 2L));
        verify(transactionHistoryRepository).save(transaction);
        verify(alertRepository, never()).save(any());
    }

    @Test
    void shouldCreateAndSaveAlertWhenRulesTrigger() {
        TransactionEvent transaction = transaction();
        RuleResult highRisk =
                RuleResult.flag("AMOUNT_THRESHOLD", Severity.HIGH, "Amount exceeds threshold");
        RuleResult mediumRisk =
                RuleResult.flag("FOREIGN_COUNTRY", Severity.MEDIUM, "Foreign transaction detected");

        when(transactionHistoryRepository.existsByTransactionId(transaction.id())).thenReturn(false);
        when(transactionHistoryRepository.countByCustomerIdSince(
                transaction.customerId(), transaction.timestamp().minusSeconds(300)))
                .thenReturn(1L);
        when(ruleEngine.evaluate(new TransactionContext(transaction, 1L)))
                .thenReturn(List.of(highRisk, mediumRisk));
        when(alertRepository.save(any(FraudAlert.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<FraudAlert> result = service.process(transaction);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().transactionId()).isEqualTo(transaction.id());
        assertThat(result.orElseThrow().customerId()).isEqualTo(transaction.customerId());
        assertThat(result.orElseThrow().highestSeverity()).isEqualTo(Severity.HIGH);
        assertThat(result.orElseThrow().triggeredRules()).containsExactly(highRisk, mediumRisk);

        ArgumentCaptor<FraudAlert> alertCaptor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().transactionId()).isEqualTo(transaction.id());
        assertThat(alertCaptor.getValue().customerId()).isEqualTo(transaction.customerId());
        verify(transactionHistoryRepository).save(transaction);
    }

    @Test
    void shouldReturnExistingAlertForDuplicateFlaggedTransaction() {
        TransactionEvent transaction = transaction();
        FraudAlert existingAlert =
                FraudAlert.from(
                        transaction,
                        List.of(RuleResult.flag("BLACKLIST_CHECK", Severity.HIGH, "Merchant is blacklisted")));

        when(transactionHistoryRepository.existsByTransactionId(transaction.id())).thenReturn(true);
        when(alertRepository.findByTransactionId(transaction.id()))
                .thenReturn(Optional.of(existingAlert));

        Optional<FraudAlert> result = service.process(transaction);

        assertThat(result).hasValue(existingAlert);
        verify(transactionHistoryRepository).lockCustomer(transaction.customerId());
        verify(alertRepository).findByTransactionId(transaction.id());
        verify(ruleEngine, never()).evaluate(any());
        verify(transactionHistoryRepository, never()).save(any());
    }

    @Test
    void shouldReturnEmptyForDuplicateCleanTransaction() {
        TransactionEvent transaction = transaction();

        when(transactionHistoryRepository.existsByTransactionId(transaction.id())).thenReturn(true);
        when(alertRepository.findByTransactionId(transaction.id())).thenReturn(Optional.empty());

        Optional<FraudAlert> result = service.process(transaction);

        assertThat(result).isEmpty();
        verify(transactionHistoryRepository).lockCustomer(transaction.customerId());
        verify(alertRepository).findByTransactionId(transaction.id());
        verify(ruleEngine, never()).evaluate(any());
        verify(transactionHistoryRepository, never()).save(any());
    }

    @Test
    void shouldReturnExistingAlertWhenTransactionSaveDetectsConcurrentDuplicate() {
        TransactionEvent transaction = transaction();
        FraudAlert existingAlert =
                FraudAlert.from(
                        transaction,
                        List.of(
                                RuleResult.flag("VELOCITY_CHECK", Severity.HIGH, "4 transactions in 5 minutes")));

        when(transactionHistoryRepository.existsByTransactionId(transaction.id())).thenReturn(false, true);
        when(transactionHistoryRepository.countByCustomerIdSince(
                transaction.customerId(), transaction.timestamp().minusSeconds(300)))
                .thenReturn(3L);
        when(ruleEngine.evaluate(new TransactionContext(transaction, 3L)))
                .thenReturn(
                        List.of(
                                RuleResult.flag("VELOCITY_CHECK", Severity.HIGH, "4 transactions in 5 minutes")));
        when(alertRepository.findByTransactionId(transaction.id()))
                .thenReturn(Optional.of(existingAlert));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate transaction"))
                .when(transactionHistoryRepository)
                .save(transaction);

        Optional<FraudAlert> result = service.process(transaction);

        assertThat(result).hasValue(existingAlert);
        verify(alertRepository).findByTransactionId(transaction.id());
        verify(alertRepository, never()).save(any());
    }

    @Test
    void shouldReturnExistingAlertWhenAlertSaveDetectsConcurrentDuplicate() {
        TransactionEvent transaction = transaction();
        RuleResult resultRule =
                RuleResult.flag("AMOUNT_THRESHOLD", Severity.HIGH, "Amount exceeds threshold");
        FraudAlert existingAlert = FraudAlert.from(transaction, List.of(resultRule));

        when(transactionHistoryRepository.existsByTransactionId(transaction.id())).thenReturn(false, true);
        when(transactionHistoryRepository.countByCustomerIdSince(
                transaction.customerId(), transaction.timestamp().minusSeconds(300)))
                .thenReturn(0L);
        when(ruleEngine.evaluate(new TransactionContext(transaction, 0L)))
                .thenReturn(List.of(resultRule));
        when(alertRepository.findByTransactionId(transaction.id()))
                .thenReturn(Optional.of(existingAlert));
        when(alertRepository.save(any(FraudAlert.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate alert"));

        Optional<FraudAlert> result = service.process(transaction);

        assertThat(result).hasValue(existingAlert);
        verify(transactionHistoryRepository).save(transaction);
        verify(alertRepository).save(any(FraudAlert.class));
        verify(alertRepository).findByTransactionId(transaction.id());
    }

    @Test
    void shouldRethrowTransactionSaveFailureWhenNoDuplicateExists() {
        TransactionEvent transaction = transaction();

        when(transactionHistoryRepository.existsByTransactionId(transaction.id())).thenReturn(false, false);
        when(transactionHistoryRepository.countByCustomerIdSince(
                transaction.customerId(), transaction.timestamp().minusSeconds(300)))
                .thenReturn(0L);
        when(ruleEngine.evaluate(new TransactionContext(transaction, 0L))).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("null customer"))
                .when(transactionHistoryRepository)
                .save(transaction);

        assertThatThrownBy(() -> service.process(transaction))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("null customer");

        verify(alertRepository, never()).findByTransactionId(transaction.id());
        verify(alertRepository, never()).save(any());
    }

    @Test
    void shouldGetAlertsByCustomerId() {
        FraudAlert firstAlert =
                FraudAlert.from(
                        transaction(),
                        List.of(
                                RuleResult.flag("AMOUNT_THRESHOLD", Severity.MEDIUM, "Amount exceeds threshold")));
        FraudAlert secondAlert =
                FraudAlert.from(
                        secondTransaction(),
                        List.of(RuleResult.flag("BLACKLIST_MATCH", Severity.HIGH, "Merchant is blacklisted")));

        when(alertRepository.findByCustomerId("CUST001")).thenReturn(List.of(firstAlert, secondAlert));

        List<FraudAlert> result = service.getByCustomerId("CUST001");

        assertThat(result).containsExactly(firstAlert, secondAlert);
        verify(alertRepository).findByCustomerId("CUST001");
    }

    @Test
    void shouldGetAlertsByStatus() {
        FraudAlert openAlert =
                FraudAlert.from(
                        transaction(),
                        List.of(RuleResult.flag("FOREIGN_COUNTRY", Severity.MEDIUM, "Foreign transaction")));

        when(alertRepository.findByStatus(AlertStatus.OPEN)).thenReturn(List.of(openAlert));

        List<FraudAlert> result = service.getByStatus(AlertStatus.OPEN);

        assertThat(result).containsExactly(openAlert);
        verify(alertRepository).findByStatus(AlertStatus.OPEN);
    }

    @Test
    void shouldGetAlertsBySeverity() {
        FraudAlert highAlert =
                FraudAlert.from(
                        secondTransaction(),
                        List.of(RuleResult.flag("BLACKLIST_MATCH", Severity.HIGH, "Merchant is blacklisted")));

        when(alertRepository.findBySeverity(Severity.HIGH)).thenReturn(List.of(highAlert));

        List<FraudAlert> result = service.getBySeverity(Severity.HIGH);

        assertThat(result).containsExactly(highAlert);
        verify(alertRepository).findBySeverity(Severity.HIGH);
    }

    @Test
    void shouldGetAlertById() {
        FraudAlert alert =
                FraudAlert.from(
                        transaction(),
                        List.of(
                                RuleResult.flag("AMOUNT_THRESHOLD", Severity.MEDIUM, "Amount exceeds threshold")));

        when(alertRepository.findById(alert.id())).thenReturn(Optional.of(alert));

        Optional<FraudAlert> result = service.getById(alert.id());

        assertThat(result).hasValue(alert);
        verify(alertRepository).findById(alert.id());
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

    @Test
    void shouldUpdateAlertStatus_whenAlertExists() {
        FraudAlert alert = FraudAlert.from(
                transaction(),
                List.of(RuleResult.flag("AMOUNT_THRESHOLD", Severity.HIGH, "Amount exceeds threshold")));

        when(alertRepository.findById(alert.id())).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(FraudAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<FraudAlert> result = service.updateStatus(alert.id(), AlertStatus.UNDER_REVIEW);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().status()).isEqualTo(AlertStatus.UNDER_REVIEW);
        assertThat(result.orElseThrow().id()).isEqualTo(alert.id());

        ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(AlertStatus.UNDER_REVIEW);
    }

    @Test
    void shouldReturnEmpty_whenAlertNotFoundForStatusUpdate() {
        UUID unknownId = UUID.randomUUID();
        when(alertRepository.findById(unknownId)).thenReturn(Optional.empty());

        Optional<FraudAlert> result = service.updateStatus(unknownId, AlertStatus.RESOLVED);

        assertThat(result).isEmpty();
        verify(alertRepository, never()).save(any());
    }

    private TransactionEvent secondTransaction() {
        return new TransactionEvent(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "CUST001",
                new BigDecimal("250.00"),
                "MERCH_BAD_001",
                "Blocked Merchant",
                TransactionCategory.ONLINE_PURCHASE,
                "ZAR",
                "ZA",
                Instant.parse("2024-01-01T10:05:00Z"));
    }
}
