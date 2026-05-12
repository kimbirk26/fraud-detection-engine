package com.kim.fraudengine.adapter.kafka.outbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.outbound.TransactionEventPublisher;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Outbound adapter: publishes transaction domain events to Kafka.
 *
 * <p>Uses {@code customerId} as the Kafka partition key to ensure all transactions for the same
 * customer are routed to the same partition. This guarantees per-customer ordering, which is
 * critical for velocity rule accuracy: the consumer processes events in order, so the
 * transaction-count window is always consistent.
 *
 * <p>Protected by a Resilience4j circuit breaker ({@code kafkaPublisher}) that opens after repeated
 * failures, preventing cascade effects when the Kafka cluster is unavailable.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTransactionPublisher implements TransactionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaTransactionPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 5L;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Spring-managed singletons - effectively immutable after context initialization")
    public KafkaTransactionPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.transactions}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    @CircuitBreaker(name = "kafkaPublisher", fallbackMethod = "publishFallback")
    @SuppressFBWarnings(
            value = "CRLF_INJECTION_LOGS",
            justification =
                    "transactionEvent.id() is a UUID and topic is a config string - neither can contain CRLF")
    public void publish(TransactionEvent transactionEvent) {
        try {
            String payload = objectMapper.writeValueAsString(transactionEvent);
            SendResult<String, String> result =
                    kafkaTemplate
                            .send(topic, transactionEvent.customerId(), payload)
                            .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info(
                    "Published transaction {} to topic {} partition={} offset={}",
                    transactionEvent.id(),
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (JsonProcessingException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish transaction event", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing transaction event", e);
        }
    }

    @SuppressWarnings("unused")
    @SuppressFBWarnings(
            value = {"CRLF_INJECTION_LOGS", "UPM_UNCALLED_PRIVATE_METHOD"},
            justification =
                    "transactionEvent.id() is a UUID; method is called reflectively by Resilience4j @CircuitBreaker")
    private void publishFallback(TransactionEvent transactionEvent, Throwable throwable) {
        log.error(
                "Circuit breaker open - failed to publish transaction {} to Kafka: {}",
                transactionEvent.id(),
                throwable.getMessage());
        throw new KafkaPublishFailureException(
                "Kafka publisher circuit breaker is open for transaction " + transactionEvent.id(),
                throwable);
    }
}
