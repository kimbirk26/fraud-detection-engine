package com.kim.fraudengine.adapter.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionStatusResponse(UUID transactionId, Status status, AlertResponse alert) {

    public enum Status {
        PENDING,
        CLEAN,
        FLAGGED
    }
}
