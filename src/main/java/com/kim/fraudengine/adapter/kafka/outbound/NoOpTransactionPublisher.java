package com.kim.fraudengine.adapter.kafka.outbound;

import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.outbound.TransactionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false")
public class NoOpTransactionPublisher implements TransactionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpTransactionPublisher.class);

    @Override
    public void publish(TransactionEvent transactionEvent) {
        log.debug("Kafka disabled — dropping transaction event {}", transactionEvent.id());
    }
}
