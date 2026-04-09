package com.kim.fraudengine.domain.model;

public record TransactionContext(
        TransactionEvent transaction,
        long recentTransactionCount) {
    public static TransactionContext from(TransactionEvent transaction, long recentTransactionCount) {
        if (transaction == null) {
            throw new IllegalArgumentException("transaction cannot be null");
        }
        return new TransactionContext(transaction, recentTransactionCount);
    }
}





