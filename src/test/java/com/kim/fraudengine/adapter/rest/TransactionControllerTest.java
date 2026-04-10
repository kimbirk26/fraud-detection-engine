package com.kim.fraudengine.adapter.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.fraudengine.adapter.rest.dto.TransactionRequest;
import com.kim.fraudengine.adapter.rest.mapper.TransactionMapper;
import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.FraudAlert;
import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.model.TransactionCategory;
import com.kim.fraudengine.domain.model.TransactionEvent;
import com.kim.fraudengine.domain.port.inbound.ProcessTransactionUseCase;
import com.kim.fraudengine.domain.port.outbound.TransactionEventPublisher;
import com.kim.fraudengine.infrastructure.security.JwtAuthenticationFilter;
import com.kim.fraudengine.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import(JwtAuthenticationFilter.class)
class TransactionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    ProcessTransactionUseCase processTransactionUseCase;

    @MockBean
    TransactionEventPublisher eventPublisher;

    @MockBean
    TransactionMapper transactionMapper;

    @MockBean
    JwtService jwtService;

    @MockBean
    UserDetailsService userDetailsService;

    private static final UUID TRANSACTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ALERT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private TransactionRequest validRequest() {
        return new TransactionRequest(
                TRANSACTION_ID, "CUST001", new BigDecimal("150.00"),
                "MERCH001", "Test Merchant",
                TransactionRequest.TransactionCategoryResponse.GROCERIES,
                "ZAR", "ZA");
    }

    private TransactionEvent sampleEvent() {
        return TransactionEvent.of(
                TRANSACTION_ID, "CUST001", new BigDecimal("150.00"),
                "MERCH001", "Test Merchant", TransactionCategory.GROCERIES, "ZAR", "ZA");
    }

    private FraudAlert sampleAlert() {
        List<RuleResult> rules = List.of(
                RuleResult.flag("AmountThreshold", Severity.HIGH, "Amount exceeds limit"));
        return new FraudAlert(
                ALERT_ID, TRANSACTION_ID, "CUST001",
                rules, Severity.HIGH, AlertStatus.OPEN, Instant.now());
    }

    // --- POST /api/v1/transactions/sync ---

    @Test
    @WithMockUser(authorities = "transactions:write")
    void submitSync_returns200_whenFraudDetected() throws Exception {
        when(transactionMapper.toEvent(any())).thenReturn(sampleEvent());
        when(processTransactionUseCase.process(any())).thenReturn(Optional.of(sampleAlert()));

        mockMvc.perform(post("/api/v1/transactions/sync").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ALERT_ID.toString()))
                .andExpect(jsonPath("$.highestSeverity").value("HIGH"));
    }

    @Test
    @WithMockUser(authorities = "transactions:write")
    void submitSync_returns204_whenNoFraudDetected() throws Exception {
        when(transactionMapper.toEvent(any())).thenReturn(sampleEvent());
        when(processTransactionUseCase.process(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/transactions/sync").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "transactions:write")
    void submitSync_returns400_whenRequestBodyInvalid() throws Exception {
        String invalidBody = """
                {
                  "customerId": "",
                  "amount": -1,
                  "merchantId": "",
                  "merchantName": ""
                }
                """;

        mockMvc.perform(post("/api/v1/transactions/sync").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    // --- POST /api/v1/transactions/async ---

    @Test
    @WithMockUser(authorities = "transactions:write")
    void submitAsync_returns202() throws Exception {
        when(transactionMapper.toEvent(any())).thenReturn(sampleEvent());

        mockMvc.perform(post("/api/v1/transactions/async").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isAccepted());

        verify(eventPublisher).publish(any());
    }

    @Test
    void submitAsync_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/async").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }
}
