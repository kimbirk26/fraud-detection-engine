package com.kim.fraudengine.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionRequest(UUID transactionId, @NotBlank String customerId, @NotNull @Positive BigDecimal amount,
                                 @NotBlank String merchantId, @NotBlank String merchantName,
                                 @NotNull TransactionCategoryResponse category,
                                 @NotBlank @Size(min = 3, max = 3) String currency,
                                 @NotBlank @Size(min = 2, max = 2) String countryCode) {

    public enum TransactionCategoryResponse {
        GROCERIES, FUEL, TRANSFER, ENTERTAINMENT, UTILITIES, TRAVEL, ONLINE_PURCHASE, ATM_WITHDRAWAL, UNKNOWN
    }
}