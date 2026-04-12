package com.kim.fraudengine.adapter.rest;

import com.kim.fraudengine.adapter.rest.dto.AlertResponse;
import com.kim.fraudengine.adapter.rest.dto.TransactionRequest;
import com.kim.fraudengine.adapter.rest.mapper.AlertMapper;
import com.kim.fraudengine.adapter.rest.mapper.TransactionMapper;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.inbound.ProcessTransactionUseCase;
import com.kim.fraudengine.domain.port.outbound.TransactionEventPublisher;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Tag(name = "Transactions", description = "Submit transactions for fraud analysis. Requires authority: transactions:write")
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

    @SuppressFBWarnings(value = "SPRING_ENDPOINT", justification = "Intentional secured REST endpoint")
    @Operation(summary = "Submit transaction asynchronously",
               description = "Publishes the transaction to Kafka for background processing. Returns 202 immediately.")
    @ApiResponse(responseCode = "202", description = "Accepted — queued for processing")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @PreAuthorize("hasAuthority('transactions:write')")
    @PostMapping("/async")
    public ResponseEntity<Void> submitAsync(@Valid @RequestBody TransactionRequest request) {
        TransactionEvent transactionEvent = toDomain(request);
        eventPublisher.publish(transactionEvent);
        return ResponseEntity.accepted().build();
    }


    @SuppressFBWarnings(value = "SPRING_ENDPOINT", justification = "Intentional secured REST endpoint")
    @Operation(summary = "Submit transaction synchronously",
               description = "Processes the transaction inline and returns a fraud alert if rules triggered, or 204 if clean.")
    @ApiResponse(responseCode = "200", description = "Fraud detected — alert returned",
                 content = @Content(schema = @Schema(implementation = AlertResponse.class)))
    @ApiResponse(responseCode = "204", description = "No fraud detected")
    @ApiResponse(responseCode = "400", description = "Validation error")
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
