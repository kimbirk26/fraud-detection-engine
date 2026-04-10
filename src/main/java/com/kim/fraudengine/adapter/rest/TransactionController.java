package com.kim.fraudengine.adapter.rest;

import com.kim.fraudengine.adapter.rest.dto.AlertResponse;
import com.kim.fraudengine.adapter.rest.dto.TransactionRequest;
import com.kim.fraudengine.adapter.rest.mapper.AlertMapper;
import com.kim.fraudengine.adapter.rest.mapper.TransactionMapper;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.inbound.ProcessTransactionUseCase;
import com.kim.fraudengine.domain.port.outbound.TransactionEventPublisher;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionEventPublisher eventPublisher;
    private final ProcessTransactionUseCase processTransactionUseCase;
    private final TransactionMapper transactionMapper;

    public TransactionController(TransactionEventPublisher eventPublisher,
                                 ProcessTransactionUseCase processTransactionUseCase,
                                 TransactionMapper transactionMapper) {
        this.eventPublisher = eventPublisher;
        this.processTransactionUseCase = processTransactionUseCase;
        this.transactionMapper = transactionMapper;
    }

    @PreAuthorize("hasAuthority('transactions:write')")
    @PostMapping("/async")
    public ResponseEntity<Void> submitAsync(@Valid @RequestBody TransactionRequest request) {
        TransactionEvent transactionEvent = toDomain(request);
        eventPublisher.publish(transactionEvent);
        return ResponseEntity.accepted().build();
    }


    @PreAuthorize("hasAuthority('transactions:write')")
    @PostMapping("/sync")
    public ResponseEntity<AlertResponse> submitSync(@Valid @RequestBody TransactionRequest request) {
        TransactionEvent transactionEvent = toDomain(request);
        Optional<AlertResponse> alert = processTransactionUseCase.process(transactionEvent)
                .map(AlertMapper::toResponse);


        return alert.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private TransactionEvent toDomain(TransactionRequest req) {
        return transactionMapper.toEvent(req);
    }
}
