package com.kim.fraudengine.application;

import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.FraudAlert;
import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionContext;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.inbound.GetAlertsUseCase;
import com.kim.fraudengine.domain.port.inbound.ProcessTransactionUseCase;
import com.kim.fraudengine.domain.port.inbound.UpdateAlertStatusUseCase;
import com.kim.fraudengine.domain.port.outbound.AlertRepository;
import com.kim.fraudengine.domain.port.outbound.TransactionHistoryRepository;
import com.kim.fraudengine.domain.rule.RuleEngine;
import com.kim.fraudengine.infrastructure.logging.SensitiveLogValueSanitizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Application service: implements both use cases, orchestrates domain logic. This is the only class
 * that touches both ports and domain objects.
 */
@Service
public final class FraudDetectionService
        implements ProcessTransactionUseCase, GetAlertsUseCase, UpdateAlertStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    private final RuleEngine ruleEngine;
    private final AlertRepository alertRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;
    private final TransactionOperations transactionOperations;
    private final int velocityWindowMinutes;

    public FraudDetectionService(
            RuleEngine ruleEngine,
            AlertRepository alertRepository,
            TransactionHistoryRepository transactionHistoryRepository,
            TransactionOperations transactionOperations,
            @Value("${app.rules.velocity.window-minutes}") int velocityWindowMinutes) {
        this.ruleEngine = ruleEngine;
        this.alertRepository = alertRepository;
        this.transactionHistoryRepository = transactionHistoryRepository;
        this.transactionOperations = transactionOperations;
        if (velocityWindowMinutes < 1) {
            throw new IllegalArgumentException("Velocity window must be positive");
        }
        this.velocityWindowMinutes = velocityWindowMinutes;
    }

    @Override
    @SuppressFBWarnings(
            value = "CRLF_INJECTION_LOGS",
            justification = "Log messages are assembled only from values normalized by safeLogValue")
    public Optional<FraudAlert> process(TransactionEvent transactionEvent) {
        log.info(
                "Evaluating transaction "
                        + safeLogValue(transactionEvent.id())
                        + " for customer "
                        + SensitiveLogValueSanitizer.normalizeForLog(transactionEvent.customerId()));
        try {
            Optional<FraudAlert> result =
                    transactionOperations.execute(status -> processInTransaction(transactionEvent));
            return Objects.requireNonNull(result, "Transaction callback returned null Optional");
        } catch (DataIntegrityViolationException ex) {
            return resolveConcurrentDuplicate(transactionEvent, ex);
        }
    }

    @SuppressFBWarnings(
            value = "CRLF_INJECTION_LOGS",
            justification = "Log messages are assembled only from values normalized by safeLogValue")
    private Optional<FraudAlert> processInTransaction(TransactionEvent transactionEvent) {
        transactionHistoryRepository.lockCustomer(transactionEvent.customerId());

        if (transactionHistoryRepository.existsByTransactionId(transactionEvent.id())) {
            Optional<FraudAlert> existingAlert =
                    alertRepository.findByTransactionId(transactionEvent.id());
            if (existingAlert.isPresent()) {
                log.info(
                        "Duplicate transaction "
                                + safeLogValue(transactionEvent.id())
                                + " received, returning existing alert "
                                + safeLogValue(existingAlert.get().id()));
            } else {
                log.info(
                        "Duplicate clean transaction "
                                + safeLogValue(transactionEvent.id())
                                + " received, returning no alert");
            }
            return existingAlert;
        }

        // Count is taken before saving this transaction; VelocityRule adds +1 to include the
        // current
        // one.
        // The advisory lock above guarantees no other thread for the same customer is between
        // these two steps, so the count is accurate.
        long recentTransactions =
                transactionHistoryRepository.countByCustomerIdSince(
                        transactionEvent.customerId(),
                        transactionEvent
                                .timestamp()
                                .minusSeconds((long) velocityWindowMinutes * 60));

        TransactionContext context = TransactionContext.from(transactionEvent, recentTransactions);
        List<RuleResult> triggered = new ArrayList<>(ruleEngine.evaluate(context));

        transactionHistoryRepository.save(transactionEvent);

        if (triggered.isEmpty()) {
            log.debug(
                    "Transaction "
                            + safeLogValue(transactionEvent.id())
                            + " passed all fraud rules");
            return Optional.empty();
        }

        FraudAlert alert = FraudAlert.from(transactionEvent, triggered);
        FraudAlert saved = alertRepository.save(alert);

        log.warn(
                "Fraud alert created: "
                        + safeLogValue(saved.id())
                        + " - severity="
                        + safeLogValue(saved.highestSeverity())
                        + ", rules="
                        + triggered.stream()
                                .map(RuleResult::ruleName)
                                .map(FraudDetectionService::safeLogValue)
                                .toList());

        return Optional.of(saved);
    }

    @SuppressFBWarnings(
            value = "CRLF_INJECTION_LOGS",
            justification = "Log messages are assembled only from values normalized by safeLogValue")
    private Optional<FraudAlert> resolveConcurrentDuplicate(
            TransactionEvent transactionEvent, DataIntegrityViolationException ex) {
        if (!transactionHistoryRepository.existsByTransactionId(transactionEvent.id())) {
            throw ex;
        }

        Optional<FraudAlert> existingAlert =
                alertRepository.findByTransactionId(transactionEvent.id());
        if (existingAlert.isPresent()) {
            log.info(
                    "Concurrent duplicate transaction "
                            + safeLogValue(transactionEvent.id())
                            + " detected, returning existing alert "
                            + safeLogValue(existingAlert.get().id()));
        } else {
            log.info(
                    "Concurrent duplicate clean transaction "
                            + safeLogValue(transactionEvent.id())
                            + " detected, returning no alert");
        }
        return existingAlert;
    }

    @Override
    public List<FraudAlert> getByCustomerId(String customerId) {
        return alertRepository.findByCustomerId(customerId);
    }

    @Override
    public List<FraudAlert> getByStatus(AlertStatus status) {
        return new ArrayList<>(alertRepository.findByStatus(status));
    }

    @Override
    public List<FraudAlert> getBySeverity(Severity severity) {
        return new ArrayList<>(alertRepository.findBySeverity(severity));
    }

    @Override
    public Optional<FraudAlert> getById(UUID id) {
        return alertRepository.findById(id);
    }

    @Override
    @SuppressFBWarnings(
            value = "CRLF_INJECTION_LOGS",
            justification = "Log messages are assembled only from values normalized by safeLogValue")
    public Optional<FraudAlert> updateStatus(UUID alertId, AlertStatus newStatus) {
        Optional<FraudAlert> existingAlert = alertRepository.findById(alertId);
        if (existingAlert.isEmpty()) {
            return Optional.empty();
        }

        FraudAlert updated = existingAlert.orElseThrow().withStatus(newStatus);
        FraudAlert saved = alertRepository.save(updated);
        log.info(
                "Alert "
                        + safeLogValue(saved.id())
                        + " status updated to "
                        + safeLogValue(saved.status()));
        return Optional.of(saved);
    }

    private static String safeLogValue(Object value) {
        return SensitiveLogValueSanitizer.normalizeForLog(Objects.toString(value, ""));
    }
}
