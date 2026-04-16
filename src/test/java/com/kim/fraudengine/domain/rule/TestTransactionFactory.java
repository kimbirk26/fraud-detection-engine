package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.TransactionCategory;
import com.kim.fraudengine.domain.model.TransactionEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

class TestTransactionFactory {

    static TransactionEvent defaultTx() {
        return new TransactionEvent(
                UUID.randomUUID(),
                "CUST001",
                BigDecimal.TEN,
                "MERCH001",
                "Test Merchant",
                TransactionCategory.ONLINE_PURCHASE,
                "ZAR",
                "ZA",
                Instant.parse("2024-01-01T10:00:00Z"));
    }

    static TransactionEvent withAmount(BigDecimal amount) {
        return defaultTx().withAmount(amount); // or rebuild if record
    }
}
