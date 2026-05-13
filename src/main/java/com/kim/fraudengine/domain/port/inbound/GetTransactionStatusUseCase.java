package com.kim.fraudengine.domain.port.inbound;

import com.kim.fraudengine.domain.model.TransactionStatus;
import java.util.UUID;

public interface GetTransactionStatusUseCase {
    TransactionStatus getStatus(UUID transactionId);
}
