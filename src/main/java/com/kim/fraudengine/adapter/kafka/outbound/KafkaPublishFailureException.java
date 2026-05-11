package com.kim.fraudengine.adapter.kafka.outbound;

public class KafkaPublishFailureException extends RuntimeException {

    public KafkaPublishFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
