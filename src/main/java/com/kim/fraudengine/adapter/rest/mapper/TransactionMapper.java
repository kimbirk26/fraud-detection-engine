package com.kim.fraudengine.adapter.rest.mapper;

import com.kim.fraudengine.adapter.rest.dto.TransactionRequest;
import com.kim.fraudengine.domain.model.TransactionEvent;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionEvent toEvent(TransactionRequest request) {
        return TransactionEvent.of(
                request.customerId(),
                request.amount(),
                request.merchantId(),
                request.merchantName(),
                CategoryMapper.toDomain(request.category()),
                request.currency(),
                request.countryCode()
        );
    }
}