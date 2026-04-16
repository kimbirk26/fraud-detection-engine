package com.kim.fraudengine.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.FraudAlert;
import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.port.inbound.GetAlertsUseCase;
import com.kim.fraudengine.domain.port.inbound.UpdateAlertStatusUseCase;
import com.kim.fraudengine.infrastructure.security.CustomerAccessEvaluator;
import com.kim.fraudengine.infrastructure.security.JwtAuthenticationFilter;
import com.kim.fraudengine.infrastructure.security.JwtService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AlertController.class)
@Import({JwtAuthenticationFilter.class, AlertControllerTest.MethodSecurityTestConfig.class})
class AlertControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean GetAlertsUseCase getAlertsUseCase;

    @MockitoBean UpdateAlertStatusUseCase updateAlertStatusUseCase;

    @MockitoBean JwtService jwtService;

    @MockitoBean UserDetailsService userDetailsService;

    @MockitoBean(name = "customerAccess")
    CustomerAccessEvaluator customerAccessEvaluator;

    private static final UUID ALERT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRANSACTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String CUSTOMER_ID = "CUST001";

    private FraudAlert sampleAlert() {
        List<RuleResult> rules =
                List.of(RuleResult.flag("AmountThreshold", Severity.HIGH, "Amount exceeds limit"));
        return new FraudAlert(
                ALERT_ID,
                TRANSACTION_ID,
                CUSTOMER_ID,
                rules,
                Severity.HIGH,
                AlertStatus.OPEN,
                Instant.now());
    }

    // --- GET /api/v1/alerts/{id} ---

    @Test
    @WithMockUser(authorities = "alerts:read")
    void getById_returns200_whenAlertFound() throws Exception {
        when(getAlertsUseCase.getById(ALERT_ID)).thenReturn(Optional.of(sampleAlert()));
        when(customerAccessEvaluator.canRead(eq(CUSTOMER_ID), any())).thenReturn(true);

        mockMvc.perform(get("/api/v1/alerts/{id}", ALERT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ALERT_ID.toString()))
                .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID))
                .andExpect(jsonPath("$.highestSeverity").value("HIGH"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @WithMockUser(authorities = "alerts:read")
    void getById_returns404_whenAlertNotFound() throws Exception {
        when(getAlertsUseCase.getById(ALERT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/alerts/{id}", ALERT_ID)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "alerts:read")
    void getById_returns404_whenAlertBelongsToAnotherCustomer() throws Exception {
        when(getAlertsUseCase.getById(ALERT_ID)).thenReturn(Optional.of(sampleAlert()));
        when(customerAccessEvaluator.canRead(eq(CUSTOMER_ID), any())).thenReturn(false);

        mockMvc.perform(get("/api/v1/alerts/{id}", ALERT_ID)).andExpect(status().isNotFound());
    }

    @Test
    void getById_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/{id}", ALERT_ID)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "transactions:write")
    void getById_returns403_whenMissingAuthority() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/{id}", ALERT_ID)).andExpect(status().isForbidden());
    }

    // --- GET /api/v1/alerts/customer/{customerId} ---

    @Test
    @WithMockUser(authorities = {"alerts:read", "alerts:read:all"})
    void getByCustomer_returns200_whenAuthorized() throws Exception {
        when(customerAccessEvaluator.canRead(anyString(), any())).thenReturn(true);
        when(getAlertsUseCase.getByCustomerId(CUSTOMER_ID)).thenReturn(List.of(sampleAlert()));

        mockMvc.perform(get("/api/v1/alerts/customer/{customerId}", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(CUSTOMER_ID));
    }

    @Test
    @WithMockUser(authorities = "alerts:read")
    void getByCustomer_returns403_whenCustomerAccessDenied() throws Exception {
        when(customerAccessEvaluator.canRead(anyString(), any())).thenReturn(false);

        mockMvc.perform(get("/api/v1/alerts/customer/{customerId}", CUSTOMER_ID))
                .andExpect(status().isForbidden());
    }

    // --- GET /api/v1/alerts?status= / ?severity= ---

    @Test
    @WithMockUser(authorities = "alerts:read:all")
    void getByFilter_byStatus_returns200() throws Exception {
        when(getAlertsUseCase.getByStatus(AlertStatus.OPEN)).thenReturn(List.of(sampleAlert()));

        mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    @WithMockUser(authorities = "alerts:read:all")
    void getByFilter_bySeverity_returns200() throws Exception {
        when(getAlertsUseCase.getBySeverity(Severity.HIGH)).thenReturn(List.of(sampleAlert()));

        mockMvc.perform(get("/api/v1/alerts").param("severity", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].highestSeverity").value("HIGH"));
    }

    @Test
    @WithMockUser(authorities = "alerts:read:all")
    void getByFilter_noParams_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/alerts")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "alerts:read")
    void getByFilter_returns403_whenMissingGlobalAuthority() throws Exception {
        mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "alerts:write")
    void updateStatus_returns200_whenAlertFound() throws Exception {
        FraudAlert updated =
                new FraudAlert(
                        ALERT_ID,
                        TRANSACTION_ID,
                        CUSTOMER_ID,
                        List.of(
                                RuleResult.flag(
                                        "AmountThreshold", Severity.HIGH, "Amount exceeds limit")),
                        Severity.HIGH,
                        AlertStatus.UNDER_REVIEW,
                        java.time.Instant.now());

        when(getAlertsUseCase.getById(ALERT_ID)).thenReturn(Optional.of(sampleAlert()));
        when(updateAlertStatusUseCase.updateStatus(ALERT_ID, AlertStatus.UNDER_REVIEW))
                .thenReturn(Optional.of(updated));

        mockMvc.perform(
                        patch("/api/v1/alerts/{id}/status", ALERT_ID)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ALERT_ID.toString()))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test
    @WithMockUser(authorities = "alerts:write")
    void updateStatus_returns404_whenAlertNotFound() throws Exception {
        when(getAlertsUseCase.getById(ALERT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(
                        patch("/api/v1/alerts/{id}/status", ALERT_ID)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "alerts:write")
    void updateStatus_returns400_whenStatusMissing() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/alerts/{id}/status", ALERT_ID)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/alerts/{id}/status", ALERT_ID)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "alerts:read")
    void updateStatus_returns403_whenMissingAuthority() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/alerts/{id}/status", ALERT_ID)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(
                            ex ->
                                    ex.authenticationEntryPoint(
                                                    (req, res, e) ->
                                                            res.sendError(
                                                                    jakarta.servlet.http
                                                                            .HttpServletResponse
                                                                            .SC_UNAUTHORIZED))
                                            .accessDeniedHandler(
                                                    (req, res, e) ->
                                                            res.sendError(
                                                                    jakarta.servlet.http
                                                                            .HttpServletResponse
                                                                            .SC_FORBIDDEN)));
            return http.build();
        }
    }
}
