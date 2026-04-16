package com.kim.fraudengine.adapter.kafka.inbound;

import com.kim.fraudengine.infrastructure.logging.SensitiveLogValueSanitizer;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: consumes messages that exhausted all retries and were routed to the dead-letter
 * topic by {@code KafkaConfig#kafkaErrorHandler}.
 *
 * <p>Logs structured details from the DLT headers added by {@link
 * org.springframework.kafka.listener.DeadLetterPublishingRecoverer} so failed messages are visible
 * in monitoring without losing context.
 */
@Component
public class TransactionEventDltConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventDltConsumer.class);

    @KafkaListener(
            topics = "${app.kafka.topics.transactions-dlt}",
            groupId = "${app.kafka.consumer-group}-dlt",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) {
        String originalTopic = headerString(record, "kafka_dlt-original-topic");
        String originalPartition = headerString(record, "kafka_dlt-original-partition");
        String originalOffset = headerString(record, "kafka_dlt-original-offset");
        String exceptionType = headerString(record, "kafka_dlt-exception-fqcn");
        String exceptionMessage = headerString(record, "kafka_dlt-exception-message");

        log.error(
                "event=dlt_message_received originalTopic={} originalPartition={} originalOffset={} "
                        + "exceptionType={} exceptionMessage={}",
                originalTopic,
                originalPartition,
                originalOffset,
                exceptionType,
                exceptionMessage);
    }

    private String headerString(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        String raw =
                header == null ? "unknown" : new String(header.value(), StandardCharsets.UTF_8);
        return SensitiveLogValueSanitizer.normalizeForLog(raw);
    }
}
