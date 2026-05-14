package com.kim.fraudengine.adapter.rest;

import com.kim.fraudengine.adapter.rest.dto.AlertResponse;
import com.kim.fraudengine.adapter.rest.dto.TransactionAcceptedResponse;
import com.kim.fraudengine.adapter.rest.dto.TransactionRequest;
import com.kim.fraudengine.adapter.rest.dto.TransactionStatusResponse;
import com.kim.fraudengine.adapter.rest.mapper.AlertMapper;
import com.kim.fraudengine.adapter.rest.mapper.TransactionMapper;
import com.kim.fraudengine.adapter.rest.mapper.TransactionStatusMapper;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.model.TransactionStatus;
import com.kim.fraudengine.domain.port.inbound.GetTransactionStatusUseCase;
import com.kim.fraudengine.domain.port.inbound.ProcessTransactionUseCase;
import com.kim.fraudengine.domain.port.outbound.TransactionEventPublisher;
import com.kim.fraudengine.infrastructure.logging.AuditLog;
import com.kim.fraudengine.infrastructure.security.CustomerAccessEvaluator;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Transactions",
        description =
                "Submit transactions for fraud analysis. Requires authority: transactions:write")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionEventPublisher eventPublisher;
    private final ProcessTransactionUseCase processTransactionUseCase;
    private final GetTransactionStatusUseCase getTransactionStatusUseCase;
    private final TransactionMapper transactionMapper;
    private final CustomerAccessEvaluator customerAccessEvaluator;

    public TransactionController(
            TransactionEventPublisher eventPublisher,
            ProcessTransactionUseCase processTransactionUseCase,
            GetTransactionStatusUseCase getTransactionStatusUseCase,
            TransactionMapper transactionMapper,
            CustomerAccessEvaluator customerAccessEvaluator) {
        this.eventPublisher = eventPublisher;
        this.processTransactionUseCase = processTransactionUseCase;
        this.getTransactionStatusUseCase = getTransactionStatusUseCase;
        this.transactionMapper = transactionMapper;
        this.customerAccessEvaluator = customerAccessEvaluator;
    }

    @SuppressFBWarnings(
            value = "SPRING_ENDPOINT",
            justification = "Intentional secured REST endpoint")
    @Operation(
            summary = "Submit transaction asynchronously",
            description =
                    "Publishes the transaction to Kafka for background processing. Returns 202 with the transaction ID.")
    @ApiResponse(
            responseCode = "202",
            description = "Accepted — queued for processing",
            content = @Content(schema = @Schema(implementation = TransactionAcceptedResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @PreAuthorize("hasAuthority('transactions:write') and @customerAccess.canWrite(#request.customerId(), authentication)")
    @PostMapping("/async")
    public ResponseEntity<TransactionAcceptedResponse> submitAsync(
            @Valid @RequestBody TransactionRequest request, Authentication authentication) {
        String authorizedCustomerId =
                customerAccessEvaluator.resolveCustomerId(
                        request.customerId(), authentication);
        TransactionEvent transactionEvent =
                transactionMapper.toEvent(request, authorizedCustomerId);
        eventPublisher.publish(transactionEvent);
        return ResponseEntity.accepted()
                .body(new TransactionAcceptedResponse(transactionEvent.id()));
    }

    @SuppressFBWarnings(
            value = "SPRING_ENDPOINT",
            justification = "Intentional secured REST endpoint")
    @Operation(
            summary = "Submit transaction synchronously",
            description =
                    "Processes the transaction inline and returns a fraud alert if rules triggered, or 204 if clean.")
    @ApiResponse(
            responseCode = "200",
            description = "Fraud detected — alert returned",
            content = @Content(schema = @Schema(implementation = AlertResponse.class)))
    @ApiResponse(responseCode = "204", description = "No fraud detected")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @PreAuthorize("hasAuthority('transactions:write') and @customerAccess.canWrite(#request.customerId(), authentication)")
    @PostMapping("/sync")
    public ResponseEntity<AlertResponse> submitSync(
            @Valid @RequestBody TransactionRequest request, Authentication authentication) {
        String authorizedCustomerId =
                customerAccessEvaluator.resolveCustomerId(
                        request.customerId(), authentication);
        TransactionEvent transactionEvent =
                transactionMapper.toEvent(request, authorizedCustomerId);
        Optional<AlertResponse> alert =
                processTransactionUseCase.process(transactionEvent).map(AlertMapper::toResponse);

        return alert.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @SuppressFBWarnings(
            value = "SPRING_ENDPOINT",
            justification = "Intentional secured REST endpoint")
    @Operation(
            summary = "Get transaction status",
            description =
                    "Returns the processing status of a transaction: PENDING, CLEAN, or FLAGGED.")
    @ApiResponse(
            responseCode = "200",
            description = "Transaction status returned",
            content = @Content(schema = @Schema(implementation = TransactionStatusResponse.class)))
    @PreAuthorize("hasAuthority('transactions:read')")
    @GetMapping("/{id}/status")
    public TransactionStatusResponse getStatus(
            @PathVariable UUID id, Authentication authentication) {
        TransactionStatus status = getTransactionStatusUseCase.getStatus(id);

        if (status.customerId() != null
                && !customerAccessEvaluator.canRead(status.customerId(), authentication)) {
            AuditLog.recordAuditLine(
                    "TRANSACTION_STATUS_ACCESS_DENIED",
                    auditDetails(authentication, id));
            return TransactionStatusMapper.toResponse(id, TransactionStatus.pending());
        }

        AuditLog.recordAuditLine(
                "TRANSACTION_STATUS_VIEWED",
                auditDetails(authentication, id));
        return TransactionStatusMapper.toResponse(id, status);
    }

    private LinkedHashMap<String, Object> auditDetails(
            Authentication authentication, UUID transactionId) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        if (authentication != null && authentication.getName() != null) {
            details.put("requestedBy", authentication.getName());
        }
        if (transactionId != null) {
            details.put("transactionId", transactionId);
        }
        return details;
    }
}
