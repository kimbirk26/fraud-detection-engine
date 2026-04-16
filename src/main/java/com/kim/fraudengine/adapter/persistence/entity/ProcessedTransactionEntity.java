package com.kim.fraudengine.adapter.persistence.entity;

import com.kim.fraudengine.domain.model.TransactionCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_transactions")
public class ProcessedTransactionEntity {

    @Id
    @Column(name = "transaction_id", nullable = false, columnDefinition = "uuid")
    private UUID transactionId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(name = "merchant_name", nullable = false)
    private String merchantName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionCategory category;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ProcessedTransactionEntity() {}

    public ProcessedTransactionEntity(
            UUID transactionId,
            String customerId,
            BigDecimal amount,
            String merchantId,
            String merchantName,
            TransactionCategory category,
            String currency,
            String countryCode,
            Instant occurredAt) {
        this.transactionId = transactionId;
        this.customerId = customerId;
        this.amount = amount;
        this.merchantId = merchantId;
        this.merchantName = merchantName;
        this.category = category;
        this.currency = currency;
        this.countryCode = countryCode;
        this.occurredAt = occurredAt;
    }
}
