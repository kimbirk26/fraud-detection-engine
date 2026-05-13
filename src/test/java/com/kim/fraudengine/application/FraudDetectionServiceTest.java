package com.kim.fraudengine.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.EvaluationOutcome;
import com.kim.fraudengine.domain.model.FraudAlert;
import com.kim.fraudengine.domain.model.RuleConfiguration;
import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionCategory;
import com.kim.fraudengine.domain.model.TransactionContext;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.model.TransactionStatus;
import com.kim.fraudengine.domain.port.outbound.AlertRepository;
import com.kim.fraudengine.domain.port.outbound.RuleConfigurationProvider;
import com.kim.fraudengine.domain.port.outbound.TransactionHistoryRepository;
import com.kim.fraudengine.domain.rule.RuleEngine;
import com.kim.fraudengine.infrastructure.observability.FraudMetrics;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock private RuleEngine ruleEngine;

    @Mock private AlertRepository alertRepository;

    @Mock private TransactionHistoryRepository transactionHistoryRepository;

    @Mock private FraudMetrics metrics;

    private RuleConfigurationProvider configProvider = List::of;

    private final TransactionOperations transactionOperations =
            new TransactionOperations() {
                @Override
                public <T> T execute(TransactionCallback<T> action) {
                    return action.doInTransaction(new SimpleTransactionStatus());
                }
            };

    private FraudDetectionService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(metrics.startTimer()).thenReturn(Timer.start());
        service =
                new FraudDetectionService(
                        ruleEngine,
                        alertRepository,
                        transactionHistoryRepository,
                        transactionOperations,
                        metrics,
                        configProvider,
                        5,
                        5);
    }

    private FraudDetectionService serviceWithConfigProvider(RuleConfigurationProvider provider) {
        return new FraudDetectionService(
                ruleEngine,
                alertRepository,
                transactionHistoryRepository,
                transactionOperations,
                metrics,
                provider,
                5,
                5);
    }

    @Test
    void shouldReturnEmptyAndSaveTransactionWhenNoRulesTrigger() {
        TransactionEvent transaction = transaction();
        Instant windowStart = transaction.timestamp().minusSeconds(300);

        when(transactionHistoryRepository.existsByTransactionId(transaction.id()))
                .thenReturn(false);
        when(transactionHistoryRepository.countByCustomerIdSince(
                        transaction.customerId(), windowStart))
                .thenReturn(2L);
        when(ruleEngine.evaluate(new TransactionContext(transaction, 2L)))
                .thenReturn(new EvaluationOutcome(List.of(), List.of(), 0, false));

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
    void shouldCreateAndSaveAlertWhenScoreExceedsThreshold() {
        TransactionEvent transaction = transaction();
        Instant expectedCorrelationSince = transaction.timestamp().minusSeconds(300);
        RuleResult highRisk =
                RuleResult.flag("AMOUNT_THRESHOLD", Severity.HIGH, "Amount exceeds threshold", 40);
        RuleResult mediumRisk =
                RuleResult.flag("FOREIGN_COUNTRY", Severity.MEDIUM, "Foreign transaction detected", 20);

        when(transactionHistoryRepository.existsByTransactionId(transaction.id()))
                .thenReturn(false);
        when(transactionHistoryRepository.countByCustomerIdSince(
                        transaction.customerId(), transaction.timestamp().minusSeconds(300)))
                .thenReturn(1L);
        when(ruleEngine.evaluate(new TransactionContext(transaction, 1L)))
                .thenReturn(new EvaluationOutcome(
                        List.of(highRisk, mediumRisk),
                        List.of(highRisk, mediumRisk),
                        60,
                        true));
        when(alertRepository.save(any(FraudAlert.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(alertRepository.findLatestOpenByCustomerId(
                        eq(transaction.customerId()), eq(expectedCorrelationSince)))
                .thenReturn(Optional.empty());

        Optional<FraudAlert> result = service.process(transaction);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().transactionId()).isEqualTo(transaction.id());
        assertThat(result.orElseThrow().customerId()).isEqualTo(transaction.customerId());
        assertThat(result.orElseThrow().highestSeverity()).isEqualTo(Severity.HIGH);
        assertThat(result.orElseThrow().triggeredRules()).containsExactly(highRisk, mediumRisk);
        assertThat(result.orElseThrow().totalScore()).isEqualTo(60);
        assertThat(result.orElseThrow().correlationGroupId()).isNotNull();

        ArgumentCaptor<FraudAlert> alertCaptor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().transactionId()).isEqualTo(transaction.id());
        assertThat(alertCaptor.getValue().totalScore()).isEqualTo(60);
        verify(transactionHistoryRepository).save(transaction);
    }

    @Test
    void shouldNotCreateAlertWhenScoreBelowThreshold() {
        TransactionEvent transaction = transaction();
        RuleResult lowRisk =
                RuleResult.flag("OUT_OF_HOURS", Severity.MEDIUM, "Transaction at 02:00", 15);

        when(transactionHistoryRepository.existsByTransactionId(transaction.id()))
                .thenReturn(false);
        when(transactionHistoryRepository.countByCustomerIdSince(
                        transaction.customerId(), transaction.timestamp().minusSeconds(300)))
                .thenReturn(1L);
        when(ruleEngine.evaluate(new TransactionContext(transaction, 1L)))
                .thenReturn(new EvaluationOutcome(
                        List.of(lowRisk),
                        List.of(lowRisk),
                        15,
                        false));

        Optional<FraudAlert> result = service.process(transaction);

        assertThat(result).isEmpty();
        verify(alertRepository, never()).save(any());
        verify(transactionHistoryRepository).save(transaction);
    }

    @Test
    void shouldReturnExistingAlertForDuplicateFlaggedTransaction() {
        TransactionEvent transaction = transaction();
        FraudAlert existingAlert =
                FraudAlert.from(
                        transaction,
                        List.of(
                                RuleResult.flag(
                                        "BLACKLIST_CHECK",
                                        Severity.HIGH,
                                        "Merchant is blacklisted",
                                        50)),
                        50);

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
                                RuleResult.flag(
                                        "VELOCITY_CHECK",
                                        Severity.HIGH,
                                        "4 transactions in 5 minutes",
                                        40)),
                        40);

        when(transactionHistoryRepository.existsByTransactionId(transaction.id()))
                .thenReturn(false, true);
        when(transactionHistoryRepository.countByCustomerIdSince(
                        transaction.customerId(), transaction.timestamp().minusSeconds(300)))
                .thenReturn(3L);
        when(ruleEngine.evaluate(new TransactionContext(transaction, 3L)))
                .thenReturn(new EvaluationOutcome(
                        List.of(RuleResult.flag("VELOCITY_CHECK", Severity.HIGH,
                                "4 transactions in 5 minutes", 40)),
                        List.of(RuleResult.flag("VELOCITY_CHECK", Severity.HIGH,
                                "4 transactions in 5 minutes", 40)),
                        40,
                        true));
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
        Instant expectedCorrelationSince = transaction.timestamp().minusSeconds(300);
        RuleResult resultRule =
                RuleResult.flag("AMOUNT_THRESHOLD", Severity.HIGH, "Amount exceeds threshold", 40);
        FraudAlert existingAlert = FraudAlert.from(transaction, List.of(resultRule), 40);

        when(transactionHistoryRepository.existsByTransactionId(transaction.id()))
                .thenReturn(false, true);
        when(transactionHistoryRepository.countByCustomerIdSince(
                        transaction.customerId(), transaction.timestamp().minusSeconds(300)))
                .thenReturn(0L);
        when(ruleEngine.evaluate(new TransactionContext(transaction, 0L)))
                .thenReturn(new EvaluationOutcome(
                        List.of(resultRule), List.of(resultRule), 40, true));
        when(alertRepository.findByTransactionId(transaction.id()))
                .thenReturn(Optional.of(existingAlert));
        when(alertRepository.findLatestOpenByCustomerId(
                        eq(transaction.customerId()), eq(expectedCorrelationSince)))
                .thenReturn(Optional.empty());
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

        when(transactionHistoryRepository.existsByTransactionId(transaction.id()))
                .thenReturn(false, false);
        when(transactionHistoryRepository.countByCustomerIdSince(
                        transaction.customerId(), transaction.timestamp().minusSeconds(300)))
                .thenReturn(0L);
        when(ruleEngine.evaluate(new TransactionContext(transaction, 0L)))
                .thenReturn(new EvaluationOutcome(List.of(), List.of(), 0, false));
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
                                RuleResult.flag(
                                        "AMOUNT_THRESHOLD",
                                        Severity.MEDIUM,
                                        "Amount exceeds threshold",
                                        20)),
                        20);
        FraudAlert secondAlert =
                FraudAlert.from(
                        secondTransaction(),
                        List.of(
                                RuleResult.flag(
                                        "BLACKLIST_MATCH",
                                        Severity.HIGH,
                                        "Merchant is blacklisted",
                                        50)),
                        50);

        when(alertRepository.findByCustomerId("CUST001"))
                .thenReturn(List.of(firstAlert, secondAlert));

        List<FraudAlert> result = service.getByCustomerId("CUST001");

        assertThat(result).containsExactly(firstAlert, secondAlert);
        verify(alertRepository).findByCustomerId("CUST001");
    }

    @Test
    void shouldGetAlertsByStatus() {
        FraudAlert openAlert =
                FraudAlert.from(
                        transaction(),
                        List.of(
                                RuleResult.flag(
                                        "FOREIGN_COUNTRY",
                                        Severity.MEDIUM,
                                        "Foreign transaction",
                                        20)),
                        20);

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
                        List.of(
                                RuleResult.flag(
                                        "BLACKLIST_MATCH",
                                        Severity.HIGH,
                                        "Merchant is blacklisted",
                                        50)),
                        50);

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
                                RuleResult.flag(
                                        "AMOUNT_THRESHOLD",
                                        Severity.MEDIUM,
                                        "Amount exceeds threshold",
                                        20)),
                        20);

        when(alertRepository.findById(alert.id())).thenReturn(Optional.of(alert));

        Optional<FraudAlert> result = service.getById(alert.id());

        assertThat(result).hasValue(alert);
        verify(alertRepository).findById(alert.id());
    }

    @Test
    void shouldReuseCorrelationGroupIdFromRecentOpenAlert() {
        TransactionEvent transaction = transaction();
        Instant expectedCorrelationSince = transaction.timestamp().minusSeconds(300);
        UUID existingGroupId = UUID.randomUUID();
        RuleResult rule = RuleResult.flag("BLACKLIST_MATCH", Severity.HIGH, "Blacklisted", 50);

        FraudAlert recentAlert = FraudAlert.from(transaction, List.of(rule), 50)
                .withCorrelationGroupId(existingGroupId);

        when(transactionHistoryRepository.existsByTransactionId(transaction.id()))
                .thenReturn(false);
        when(transactionHistoryRepository.countByCustomerIdSince(
                        transaction.customerId(), transaction.timestamp().minusSeconds(300)))
                .thenReturn(0L);
        when(ruleEngine.evaluate(new TransactionContext(transaction, 0L)))
                .thenReturn(new EvaluationOutcome(
                        List.of(rule), List.of(rule), 50, true));
        when(alertRepository.findLatestOpenByCustomerId(
                        eq(transaction.customerId()), eq(expectedCorrelationSince)))
                .thenReturn(Optional.of(recentAlert));
        when(alertRepository.save(any(FraudAlert.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<FraudAlert> result = service.process(transaction);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().correlationGroupId()).isEqualTo(existingGroupId);
    }

    @Test
    void shouldUseEventTimeForCorrelationWindow_notWallClock() {
        // Use a very old event time to prove event time is used, not Instant.now()
        TransactionEvent oldTransaction = new TransactionEvent(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "CUST001",
                new BigDecimal("1500.00"),
                "MERCH001",
                "Test Merchant",
                TransactionCategory.ONLINE_PURCHASE,
                "ZAR",
                "ZA",
                Instant.parse("2020-06-15T12:00:00Z"));

        Instant expectedCorrelationSince = oldTransaction.timestamp().minusSeconds(300);
        RuleResult rule = RuleResult.flag("AMOUNT_THRESHOLD", Severity.HIGH, "Amount high", 50);

        when(transactionHistoryRepository.existsByTransactionId(oldTransaction.id()))
                .thenReturn(false);
        when(transactionHistoryRepository.countByCustomerIdSince(
                        oldTransaction.customerId(), oldTransaction.timestamp().minusSeconds(300)))
                .thenReturn(0L);
        when(ruleEngine.evaluate(new TransactionContext(oldTransaction, 0L)))
                .thenReturn(new EvaluationOutcome(
                        List.of(rule), List.of(rule), 50, true));
        when(alertRepository.findLatestOpenByCustomerId(
                        eq(oldTransaction.customerId()), eq(expectedCorrelationSince)))
                .thenReturn(Optional.empty());
        when(alertRepository.save(any(FraudAlert.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<FraudAlert> result = service.process(oldTransaction);

        assertThat(result).isPresent();
        // Verify correlation used the event time (2020-06-15T11:55:00Z), not wall clock
        verify(alertRepository).findLatestOpenByCustomerId(
                eq(oldTransaction.customerId()), eq(expectedCorrelationSince));
    }

    // --- Config parsing tests ---

    @Test
    void shouldFallBackToDefaultWhenWindowMinutesIsNonNumeric() {
        RuleConfigurationProvider badConfigProvider = () -> List.of(
                new RuleConfiguration("VELOCITY_CHECK", true, 30,
                        Map.of("windowMinutes", "ten")));

        FraudDetectionService svc = serviceWithConfigProvider(badConfigProvider);

        TransactionEvent transaction = transaction();
        // Default window is 5 minutes = 300 seconds
        Instant expectedWindowStart = transaction.timestamp().minusSeconds(300);

        when(transactionHistoryRepository.existsByTransactionId(transaction.id()))
                .thenReturn(false);
        when(transactionHistoryRepository.countByCustomerIdSince(
                        transaction.customerId(), expectedWindowStart))
                .thenReturn(0L);
        when(ruleEngine.evaluate(new TransactionContext(transaction, 0L)))
                .thenReturn(new EvaluationOutcome(List.of(), List.of(), 0, false));

        svc.process(transaction);

        verify(transactionHistoryRepository)
                .countByCustomerIdSince(transaction.customerId(), expectedWindowStart);
    }

    @Test
    void shouldFallBackToDefaultWhenWindowMinutesIsNegative() {
        RuleConfigurationProvider badConfigProvider = () -> List.of(
                new RuleConfiguration("VELOCITY_CHECK", true, 30,
                        Map.of("windowMinutes", "-1")));

        FraudDetectionService svc = serviceWithConfigProvider(badConfigProvider);

        TransactionEvent transaction = transaction();
        Instant expectedWindowStart = transaction.timestamp().minusSeconds(300);

        when(transactionHistoryRepository.existsByTransactionId(transaction.id()))
                .thenReturn(false);
        when(transactionHistoryRepository.countByCustomerIdSince(
                        transaction.customerId(), expectedWindowStart))
                .thenReturn(0L);
        when(ruleEngine.evaluate(new TransactionContext(transaction, 0L)))
                .thenReturn(new EvaluationOutcome(List.of(), List.of(), 0, false));

        svc.process(transaction);

        verify(transactionHistoryRepository)
                .countByCustomerIdSince(transaction.customerId(), expectedWindowStart);
    }

    @Test
    void shouldUseConfiguredWindowMinutesWhenValid() {
        RuleConfigurationProvider goodConfigProvider = () -> List.of(
                new RuleConfiguration("VELOCITY_CHECK", true, 30,
                        Map.of("windowMinutes", "10")));

        FraudDetectionService svc = serviceWithConfigProvider(goodConfigProvider);

        TransactionEvent transaction = transaction();
        // 10 minutes = 600 seconds
        Instant expectedWindowStart = transaction.timestamp().minusSeconds(600);

        when(transactionHistoryRepository.existsByTransactionId(transaction.id()))
                .thenReturn(false);
        when(transactionHistoryRepository.countByCustomerIdSince(
                        transaction.customerId(), expectedWindowStart))
                .thenReturn(0L);
        when(ruleEngine.evaluate(new TransactionContext(transaction, 0L)))
                .thenReturn(new EvaluationOutcome(List.of(), List.of(), 0, false));

        svc.process(transaction);

        verify(transactionHistoryRepository)
                .countByCustomerIdSince(transaction.customerId(), expectedWindowStart);
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
        FraudAlert alert =
                FraudAlert.from(
                        transaction(),
                        List.of(
                                RuleResult.flag(
                                        "AMOUNT_THRESHOLD",
                                        Severity.HIGH,
                                        "Amount exceeds threshold",
                                        40)),
                        40);

        when(alertRepository.findById(alert.id())).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(FraudAlert.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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

    @Test
    void getStatus_shouldReturnPending_whenTransactionNotFound() {
        UUID txId = UUID.randomUUID();
        when(transactionHistoryRepository.findCustomerIdByTransactionId(txId))
                .thenReturn(Optional.empty());

        TransactionStatus result = service.getStatus(txId);

        assertThat(result.state()).isEqualTo(TransactionStatus.State.PENDING);
        assertThat(result.customerId()).isNull();
        assertThat(result.alert()).isEmpty();
        verify(alertRepository, never()).findByTransactionId(any());
    }

    @Test
    void getStatus_shouldReturnClean_whenTransactionFoundWithNoAlert() {
        UUID txId = UUID.randomUUID();
        when(transactionHistoryRepository.findCustomerIdByTransactionId(txId))
                .thenReturn(Optional.of("CUST001"));
        when(alertRepository.findByTransactionId(txId)).thenReturn(Optional.empty());

        TransactionStatus result = service.getStatus(txId);

        assertThat(result.state()).isEqualTo(TransactionStatus.State.CLEAN);
        assertThat(result.customerId()).isEqualTo("CUST001");
        assertThat(result.alert()).isEmpty();
    }

    @Test
    void getStatus_shouldReturnFlagged_whenTransactionFoundWithAlert() {
        UUID txId = UUID.randomUUID();
        FraudAlert alert =
                FraudAlert.from(
                        transaction(),
                        List.of(
                                RuleResult.flag(
                                        "AMOUNT_THRESHOLD",
                                        Severity.HIGH,
                                        "Amount exceeds threshold",
                                        40)),
                        40);
        when(transactionHistoryRepository.findCustomerIdByTransactionId(txId))
                .thenReturn(Optional.of("CUST001"));
        when(alertRepository.findByTransactionId(txId)).thenReturn(Optional.of(alert));

        TransactionStatus result = service.getStatus(txId);

        assertThat(result.state()).isEqualTo(TransactionStatus.State.FLAGGED);
        assertThat(result.customerId()).isEqualTo("CUST001");
        assertThat(result.alert()).hasValue(alert);
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
