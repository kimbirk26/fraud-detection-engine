package com.kim.fraudengine.infrastructure.logging;

import org.slf4j.MDC;

/**
 * AutoCloseable utility that pushes {@code customerId} and {@code transactionId} into MDC during
 * transaction processing. Used in {@code FraudDetectionService.process()} and {@code
 * TransactionEventConsumer.consume()} to provide structured context in log output.
 */
public final class TransactionMdcContext implements AutoCloseable {

    private static final String KEY_CUSTOMER_ID = "customerId";
    private static final String KEY_TRANSACTION_ID = "transactionId";

    public TransactionMdcContext(String customerId, String transactionId) {
        MDC.put(KEY_CUSTOMER_ID, customerId);
        MDC.put(KEY_TRANSACTION_ID, transactionId);
    }

    @Override
    public void close() {
        MDC.remove(KEY_CUSTOMER_ID);
        MDC.remove(KEY_TRANSACTION_ID);
    }
}
