package com.kim.fraudengine.adapter.kafka.outbound;

import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.outbound.TransactionEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-op outbound adapter used when {@code app.kafka.enabled=false}. Async transaction publishing is
 * silently dropped; the synchronous evaluation endpoint continues to work normally.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false")
public class NoOpTransactionEventPublisher implements TransactionEventPublisher {

    @Override
    public void publish(TransactionEvent transactionEvent) {
        // Kafka disabled — event not forwarded to the message bus
    }
}
