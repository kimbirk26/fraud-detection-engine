package com.kim.fraudengine.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionEvent(
        UUID id,
        String customerId,
        BigDecimal amount,
        String merchantId,
        String merchantName,
        TransactionCategory category,
        String currency,
        String countryCode,
        Instant timestamp) {
    public static TransactionEvent of(
            String customerId,
            BigDecimal amount,
            String merchantId,
            String merchantName,
            TransactionCategory category,
            String currency,
            String countryCode) {
        return of(
                null,
                customerId,
                amount,
                merchantId,
                merchantName,
                category,
                currency,
                countryCode);
    }

    public static TransactionEvent of(
            UUID transactionId,
            String customerId,
            BigDecimal amount,
            String merchantId,
            String merchantName,
            TransactionCategory category,
            String currency,
            String countryCode) {
        return of(
                transactionId,
                customerId,
                amount,
                merchantId,
                merchantName,
                category,
                currency,
                countryCode,
                null);
    }

    public static TransactionEvent of(
            UUID transactionId,
            String customerId,
            BigDecimal amount,
            String merchantId,
            String merchantName,
            TransactionCategory category,
            String currency,
            String countryCode,
            Instant timestamp) {
        return new TransactionEvent(
                transactionId != null ? transactionId : UUID.randomUUID(),
                customerId,
                amount,
                merchantId,
                merchantName,
                category,
                currency,
                countryCode,
                timestamp != null ? timestamp : Instant.now());
    }

    public TransactionEvent withAmount(BigDecimal newAmount) {
        return new TransactionEvent(
                this.id,
                this.customerId,
                newAmount,
                this.merchantId,
                this.merchantName,
                this.category,
                this.currency,
                this.countryCode,
                this.timestamp);
    }
}
