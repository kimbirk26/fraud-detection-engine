package com.kim.fraudengine.infrastructure.observability;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Encapsulates all custom Micrometer metrics for fraud detection. Metrics follow the naming
 * convention {@code fraud.<domain>.<action>} to allow easy discovery and dashboarding.
 */
@Component
public class FraudMetrics {

    private final MeterRegistry registry;

    private final Counter transactionsClean;
    private final Counter transactionsFlagged;
    private final Timer processingTimer;
    private final DistributionSummary scoreSummary;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Spring-managed singleton - effectively immutable after context initialization")
    public FraudMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.transactionsClean = Counter.builder("fraud.transactions.processed")
                .tag("outcome", "clean")
                .description("Number of transactions that passed all rules")
                .register(registry);

        this.transactionsFlagged = Counter.builder("fraud.transactions.processed")
                .tag("outcome", "flagged")
                .description("Number of transactions that triggered an alert")
                .register(registry);

        this.processingTimer = Timer.builder("fraud.transaction.processing.duration")
                .description("Time to evaluate a transaction through the rule engine")
                .register(registry);

        this.scoreSummary = DistributionSummary.builder("fraud.scoring.total_score")
                .description("Distribution of total fraud scores across all evaluations")
                .register(registry);
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopTimer(Timer.Sample sample) {
        sample.stop(processingTimer);
    }

    public void recordClean() {
        transactionsClean.increment();
    }

    public void recordFlagged() {
        transactionsFlagged.increment();
    }

    public void recordAlertCreated(String ruleName, String severity) {
        Counter.builder("fraud.alerts.created")
                .tag("rule", ruleName)
                .tag("severity", severity)
                .register(registry)
                .increment();
    }

    public void recordStatusChange(String fromStatus, String toStatus) {
        Counter.builder("fraud.alerts.status_changed")
                .tag("from", fromStatus)
                .tag("to", toStatus)
                .register(registry)
                .increment();
    }

    public void recordScore(int totalScore, boolean thresholdExceeded) {
        scoreSummary.record(totalScore);
    }
}
