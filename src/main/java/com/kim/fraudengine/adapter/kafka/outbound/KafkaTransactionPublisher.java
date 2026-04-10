package com.kim.fraudengine.adapter.kafka.outbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.outbound.TransactionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Outbound adapter: publishes transaction domain events to Kafka.
 */
@Component
public class KafkaTransactionPublisher implements TransactionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaTransactionPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 5L;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaTransactionPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper,
                                     @Value("${app.kafka.topics.transactions}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void publish(TransactionEvent transactionEvent) {
        try {
            String payload = objectMapper.writeValueAsString(transactionEvent);
            SendResult<String, String> result = kafkaTemplate
                    .send(topic, transactionEvent.customerId(), payload)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("Published transaction {} to topic {} partition={} offset={}",
                    transactionEvent.id(),
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (JsonProcessingException | ExecutionException | TimeoutException e) {
            log.error("Failed to publish transaction {}", transactionEvent.id(), e);
            throw new IllegalStateException("Failed to publish transaction event", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing transaction event", e);
        }
    }
}
