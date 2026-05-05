package com.kim.fraudengine.adapter.kafka.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.inbound.ProcessTransactionUseCase;
import com.kim.fraudengine.infrastructure.logging.SensitiveLogValueSanitizer;
import com.kim.fraudengine.infrastructure.security.InternalAuthenticationRunner;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Inbound adapter: consumes transaction events from Kafka and delegates to the use case. */
@SuppressFBWarnings(
        value = "CRLF_INJECTION_LOGS",
        justification =
                "alert.id() is a UUID and highestSeverity() is an enum - neither can contain CRLF; "
                        + "SpotBugs cannot see through the ifPresent lambda to the method-level suppression")
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);
    private static final String INTERNAL_PRINCIPAL_NAME = "system:kafka-consumer";

    private final ProcessTransactionUseCase processTransactionUseCase;
    private final ObjectMapper objectMapper;
    private final InternalAuthenticationRunner internalAuthenticationRunner;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Spring-managed singleton - effectively immutable after context initialization")
    public TransactionEventConsumer(
            ProcessTransactionUseCase processTransactionUseCase,
            ObjectMapper objectMapper,
            InternalAuthenticationRunner internalAuthenticationRunner) {
        this.processTransactionUseCase = processTransactionUseCase;
        this.objectMapper = objectMapper;
        this.internalAuthenticationRunner = internalAuthenticationRunner;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.transactions}",
            groupId = "${app.kafka.consumer-group}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset)
            throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(
                    "Received message from topic={} offset={}",
                    SensitiveLogValueSanitizer.normalizeForLog(topic),
                    offset);
        }
        TransactionEvent transactionEvent = objectMapper.readValue(payload, TransactionEvent.class);
        internalAuthenticationRunner
                .runAs(
                        INTERNAL_PRINCIPAL_NAME,
                        List.of(InternalAuthenticationRunner.INTERNAL_PROCESSING_AUTHORITY),
                        () -> processTransactionUseCase.process(transactionEvent))
                .ifPresent(
                        alert ->
                                log.warn(
                                        "Alert raised: {} severity={}",
                                        alert.id(),
                                        alert.highestSeverity()));
    }
}
