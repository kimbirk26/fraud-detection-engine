package com.kim.fraudengine.adapter.rest.mapper;

import com.kim.fraudengine.adapter.rest.dto.AlertResponse;
import com.kim.fraudengine.adapter.rest.dto.TransactionStatusResponse;
import com.kim.fraudengine.adapter.rest.dto.TransactionStatusResponse.Status;
import com.kim.fraudengine.domain.model.TransactionStatus;
import java.util.UUID;

public final class TransactionStatusMapper {

    private TransactionStatusMapper() {}

    public static TransactionStatusResponse toResponse(
            UUID transactionId, TransactionStatus status) {
        AlertResponse alertResponse =
                status.alert().map(AlertMapper::toResponse).orElse(null);
        return new TransactionStatusResponse(
                transactionId, toStatus(status.state()), alertResponse);
    }

    private static Status toStatus(TransactionStatus.State state) {
        return switch (state) {
            case PENDING -> Status.PENDING;
            case CLEAN -> Status.CLEAN;
            case FLAGGED -> Status.FLAGGED;
        };
    }
}
