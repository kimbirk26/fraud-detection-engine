package com.kim.fraudengine.domain.port.inbound;

import com.kim.fraudengine.domain.model.FraudAlert;
import com.kim.fraudengine.domain.model.TransactionEvent;
import java.util.Optional;

/** Inbound port: the domain's contract for processing a transaction. */
public interface ProcessTransactionUseCase {

    Optional<FraudAlert> process(TransactionEvent transactionEvent);
}
