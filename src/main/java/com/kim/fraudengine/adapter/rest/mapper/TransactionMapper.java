package com.kim.fraudengine.adapter.rest.mapper;

import com.kim.fraudengine.adapter.rest.dto.TransactionRequest;
import com.kim.fraudengine.domain.model.TransactionEvent;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionEvent toEvent(TransactionRequest request) {
        return toEvent(request, request.customerId());
    }

    public TransactionEvent toEvent(TransactionRequest request, String authorizedCustomerId) {
        return TransactionEvent.of(
                request.transactionId(),
                authorizedCustomerId,
                request.amount(),
                request.merchantId(),
                request.merchantName(),
                CategoryMapper.toDomain(request.category()),
                request.currency(),
                request.countryCode());
    }
}
