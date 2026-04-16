package com.kim.fraudengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.fraudengine.adapter.rest.dto.TransactionRequest;
import com.kim.fraudengine.adapter.rest.dto.TransactionRequest.TransactionCategoryResponse;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end integration tests for the fraud detection pipeline. Each test exercises the full
 * stack: HTTP → security → service → rules → JPA → Postgres, asserting the HTTP response.
 */
class FraudDetectionIntegrationTest extends AbstractIntegrationTest {

    private static final String SYNC_URL = "/api/v1/transactions/sync";

    @Autowired MockMvc mockMvc;

    @Autowired ObjectMapper objectMapper;

    // -----------------------------------------------------------------------
    // Scenario 1 — clean transaction
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = "transactions:write")
    void cleanTransaction_returns204() throws Exception {
        TransactionRequest request =
                new TransactionRequest(
                        UUID.randomUUID(),
                        "CUST-IT-001",
                        new BigDecimal("100.00"),
                        "MERCH-SAFE-001",
                        "Safe Merchant",
                        TransactionCategoryResponse.GROCERIES,
                        "ZAR",
                        "ZA");

        mockMvc.perform(
                        post(SYNC_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    // -----------------------------------------------------------------------
    // Scenario 2 — blacklisted merchant
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = "transactions:write")
    void blacklistedMerchant_returns200WithBlacklistAlert() throws Exception {
        TransactionRequest request =
                new TransactionRequest(
                        UUID.randomUUID(),
                        "CUST-IT-002",
                        new BigDecimal("100.00"),
                        "MERCH_FRAUD_001", // blacklisted in application.yml
                        "Fraudulent Merchant",
                        TransactionCategoryResponse.ONLINE_PURCHASE,
                        "ZAR",
                        "ZA");

        mockMvc.perform(
                        post(SYNC_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highestSeverity").value("HIGH"))
                .andExpect(
                        jsonPath("$.triggeredRules[?(@.ruleName == 'BLACKLIST_MATCH')]").exists());
    }

    // -----------------------------------------------------------------------
    // Scenario 3 — duplicate transaction is idempotent
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = "transactions:write")
    void duplicateTransaction_isIdempotent() throws Exception {
        UUID transactionId = UUID.randomUUID();
        TransactionRequest request =
                new TransactionRequest(
                        transactionId,
                        "CUST-IT-003",
                        new BigDecimal("100.00"),
                        "MERCH_FRAUD_001", // blacklisted — ensures an alert is created
                        "Fraudulent Merchant",
                        TransactionCategoryResponse.ONLINE_PURCHASE,
                        "ZAR",
                        "ZA");

        String body = objectMapper.writeValueAsString(request);

        String firstResponse =
                mockMvc.perform(
                                post(SYNC_URL)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String secondResponse =
                mockMvc.perform(
                                post(SYNC_URL)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String firstId = objectMapper.readTree(firstResponse).get("id").asText();
        String secondId = objectMapper.readTree(secondResponse).get("id").asText();
        assertThat(firstId).isEqualTo(secondId);
    }

    // -----------------------------------------------------------------------
    // Scenario 4 — analyst moves a flagged alert to UNDER_REVIEW
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = {"transactions:write", "alerts:write"})
    void flaggedAlert_canBeMovedToUnderReview() throws Exception {
        TransactionRequest request =
                new TransactionRequest(
                        UUID.randomUUID(),
                        "CUST-IT-004",
                        new BigDecimal("100.00"),
                        "MERCH_FRAUD_002", // blacklisted in application.yml
                        "Fraudulent Merchant 2",
                        TransactionCategoryResponse.ONLINE_PURCHASE,
                        "ZAR",
                        "ZA");

        String alertJson =
                mockMvc.perform(
                                post(SYNC_URL)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String alertId = objectMapper.readTree(alertJson).get("id").asText();

        mockMvc.perform(
                        patch("/api/v1/alerts/{id}/status", alertId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alertId))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }
}
