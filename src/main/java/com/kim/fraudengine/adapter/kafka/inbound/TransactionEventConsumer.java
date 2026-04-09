package com.kim.fraudengine.adapter.kafka.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.inbound.ProcessTransactionUseCase;
import com.kim.fraudengine.infrastructure.security.InternalAuthenticationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Inbound adapter: consumes transaction events from Kafka and delegates to the use case.
 */
@Component
public class TransactionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);
    private static final String INTERNAL_PRINCIPAL_NAME = "system:kafka-consumer";

    private final ProcessTransactionUseCase processTransactionUseCase;
    private final ObjectMapper objectMapper;
    private final InternalAuthenticationRunner internalAuthenticationRunner;

    public TransactionEventConsumer(ProcessTransactionUseCase processTransactionUseCase,
                                    ObjectMapper objectMapper,
                                    InternalAuthenticationRunner internalAuthenticationRunner) {
        this.processTransactionUseCase = processTransactionUseCase;
        this.objectMapper = objectMapper;
        this.internalAuthenticationRunner = internalAuthenticationRunner;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.transactions}",
            groupId = "${app.kafka.consumer-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload String payload,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(KafkaHeaders.OFFSET) long offset) throws Exception {
        log.debug("Received message from topic={} offset={}", topic, offset);
        TransactionEvent transactionEvent = objectMapper.readValue(payload, TransactionEvent.class);
        internalAuthenticationRunner.runAs(
                        INTERNAL_PRINCIPAL_NAME,
                        List.of(InternalAuthenticationRunner.INTERNAL_PROCESSING_AUTHORITY),
                        () -> processTransactionUseCase.process(transactionEvent))
                .ifPresent(alert ->
                        log.warn("Alert raised: {} severity={}", alert.id(), alert.highestSeverity()));
    }
}
