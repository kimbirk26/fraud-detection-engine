package com.kim.fraudengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.fraudengine.adapter.rest.dto.TransactionRequest;
import com.kim.fraudengine.adapter.rest.dto.TransactionRequest.TransactionCategoryResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end async pipeline test exercising: REST controller -> Kafka publish -> consumer pickup ->
 * FraudDetectionService -> rule evaluation with scoring -> alert persistence with correlation ->
 * REST query.
 */
class KafkaAsyncPipelineIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Autowired ObjectMapper objectMapper;

    @Test
    @WithMockUser(authorities = {"transactions:write", "alerts:read", "alerts:read:all", "ROLE_ADMIN"})
    void asyncPipeline_publishesAndConsumesTransaction_creatingAlert() throws Exception {
        String customerId = "CUST-ASYNC-" + UUID.randomUUID().toString().substring(0, 8);
        UUID transactionId = UUID.randomUUID();

        TransactionRequest request =
                new TransactionRequest(
                        transactionId,
                        customerId,
                        new BigDecimal("100.00"),
                        "MERCH_FRAUD_001", // blacklisted -> score=50 -> exceeds threshold=40
                        "Fraudulent Merchant",
                        TransactionCategoryResponse.ONLINE_PURCHASE,
                        "ZAR",
                        "ZA");

        // POST to async endpoint - expect 202 Accepted
        mockMvc.perform(
                        post("/api/v1/transactions/async")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        // Poll for alert to appear via the REST query endpoint
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(
                        () -> {
                            MvcResult result =
                                    mockMvc.perform(
                                                    get("/api/v1/alerts/customer/{customerId}",
                                                            customerId))
                                            .andExpect(status().isOk())
                                            .andReturn();

                            String responseBody = result.getResponse().getContentAsString();
                            JsonNode alerts = objectMapper.readTree(responseBody);
                            assertThat(alerts).isNotEmpty();

                            JsonNode alert = alerts.get(0);
                            assertThat(alert.get("customerId").asText()).isEqualTo(customerId);
                            assertThat(alert.get("highestSeverity").asText()).isEqualTo("HIGH");
                            assertThat(alert.get("totalScore").asInt()).isEqualTo(50);
                            assertThat(alert.get("correlationGroupId")).isNotNull();
                            assertThat(alert.get("triggeredRules")).isNotEmpty();

                            JsonNode triggeredRule = alert.get("triggeredRules").get(0);
                            assertThat(triggeredRule.get("ruleName").asText())
                                    .isEqualTo("BLACKLIST_MATCH");
                            assertThat(triggeredRule.get("score").asInt()).isEqualTo(50);
                        });
    }
}
