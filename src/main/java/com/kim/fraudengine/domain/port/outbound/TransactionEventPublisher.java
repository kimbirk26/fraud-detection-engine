package com.kim.fraudengine.domain.port.outbound;

import com.kim.fraudengine.domain.model.TransactionEvent;

/** Outbound port: publishing a transaction event to the message bus. */
public interface TransactionEventPublisher {
    void publish(TransactionEvent transactionEvent);
}
