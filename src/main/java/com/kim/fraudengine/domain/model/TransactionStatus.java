package com.kim.fraudengine.domain.model;

import java.util.Optional;

public record TransactionStatus(State state, String customerId, Optional<FraudAlert> alert) {

    public enum State {
        PENDING,
        CLEAN,
        FLAGGED
    }

    public static TransactionStatus pending() {
        return new TransactionStatus(State.PENDING, null, Optional.empty());
    }

    public static TransactionStatus clean(String customerId) {
        return new TransactionStatus(State.CLEAN, customerId, Optional.empty());
    }

    public static TransactionStatus flagged(String customerId, FraudAlert alert) {
        return new TransactionStatus(State.FLAGGED, customerId, Optional.of(alert));
    }
}
