package com.kim.fraudengine.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.fraudengine.adapter.persistence.JpaRuleConfigurationProvider;
import com.kim.fraudengine.adapter.persistence.RuleConfigurationJpaRepository;
import com.kim.fraudengine.adapter.persistence.entity.RuleConfigurationEntity;
import com.kim.fraudengine.domain.model.RuleConfiguration;
import com.kim.fraudengine.infrastructure.security.JwtAuthenticationFilter;
import com.kim.fraudengine.infrastructure.security.JwtService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RuleConfigController.class)
@Import({JwtAuthenticationFilter.class, RuleConfigControllerTest.MethodSecurityTestConfig.class})
class RuleConfigControllerTest {

    @Autowired MockMvc mockMvc;

    @Autowired ObjectMapper objectMapper;

    @MockitoBean JpaRuleConfigurationProvider configProvider;

    @MockitoBean RuleConfigurationJpaRepository repository;

    @MockitoBean JwtService jwtService;

    @MockitoBean UserDetailsService userDetailsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void listAll_returns200_forAdmin() throws Exception {
        when(configProvider.getActiveRuleConfigurations())
                .thenReturn(
                        List.of(
                                new RuleConfiguration(
                                        "VELOCITY_CHECK",
                                        true,
                                        40,
                                        Map.of("maxTransactions", "5"))));

        mockMvc.perform(get("/api/v1/admin/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ruleName").value("VELOCITY_CHECK"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[0].score").value(40))
                .andExpect(jsonPath("$[0].parameters.maxTransactions").value("5"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getOne_returns200_whenRuleExists() throws Exception {
        when(configProvider.getConfiguration("VELOCITY_CHECK"))
                .thenReturn(
                        Optional.of(
                                new RuleConfiguration(
                                        "VELOCITY_CHECK",
                                        true,
                                        40,
                                        Map.of("maxTransactions", "5"))));

        mockMvc.perform(get("/api/v1/admin/rules/VELOCITY_CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleName").value("VELOCITY_CHECK"))
                .andExpect(jsonPath("$.score").value(40));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getOne_returns404_whenRuleNotFound() throws Exception {
        when(configProvider.getConfiguration("UNKNOWN_RULE")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/admin/rules/UNKNOWN_RULE"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_returns200_whenRuleExists() throws Exception {
        RuleConfigurationEntity entity =
                new RuleConfigurationEntity(
                        "VELOCITY_CHECK", true, 40, "{}", Instant.now());
        when(repository.findById("VELOCITY_CHECK")).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String body =
                objectMapper.writeValueAsString(
                        Map.of(
                                "enabled", true,
                                "score", 50,
                                "parameters",
                                        Map.of("maxTransactions", "10", "windowMinutes", "5")));

        mockMvc.perform(
                        put("/api/v1/admin/rules/VELOCITY_CHECK")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleName").value("VELOCITY_CHECK"))
                .andExpect(jsonPath("$.score").value(50))
                .andExpect(jsonPath("$.parameters.maxTransactions").value("10"));

        verify(configProvider).evictCache();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_returns404_whenRuleNotFound() throws Exception {
        when(repository.findById("UNKNOWN_RULE")).thenReturn(Optional.empty());

        String body =
                objectMapper.writeValueAsString(
                        Map.of("enabled", true, "score", 50, "parameters", Map.of()));

        mockMvc.perform(
                        put("/api/v1/admin/rules/UNKNOWN_RULE")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "alerts:read")
    void listAll_returns403_forNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/rules")).andExpect(status().isForbidden());
    }

    @Test
    void listAll_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/rules")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_returns400_whenBodyInvalid() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("score", -1));

        mockMvc.perform(
                        put("/api/v1/admin/rules/VELOCITY_CHECK")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
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
