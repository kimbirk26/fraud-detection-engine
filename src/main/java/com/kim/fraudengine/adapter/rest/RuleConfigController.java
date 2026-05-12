package com.kim.fraudengine.adapter.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.fraudengine.adapter.persistence.JpaRuleConfigurationProvider;
import com.kim.fraudengine.adapter.persistence.RuleConfigurationJpaRepository;
import com.kim.fraudengine.adapter.persistence.entity.RuleConfigurationEntity;
import com.kim.fraudengine.adapter.rest.dto.RuleConfigurationResponse;
import com.kim.fraudengine.adapter.rest.dto.UpdateRuleConfigurationRequest;
import com.kim.fraudengine.domain.model.RuleConfiguration;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/rules")
public class RuleConfigController {

    private final JpaRuleConfigurationProvider configProvider;
    private final RuleConfigurationJpaRepository repository;
    private final ObjectMapper objectMapper;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Spring-managed singletons - effectively immutable after context initialization")
    public RuleConfigController(
            JpaRuleConfigurationProvider configProvider,
            RuleConfigurationJpaRepository repository,
            ObjectMapper objectMapper) {
        this.configProvider = configProvider;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @SuppressFBWarnings(
            value = "SPRING_ENDPOINT",
            justification = "Intentional secured REST endpoint")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<RuleConfigurationResponse> listAll() {
        return configProvider.getActiveRuleConfigurations().stream()
                .map(this::toResponse)
                .toList();
    }

    @SuppressFBWarnings(
            value = "SPRING_ENDPOINT",
            justification = "Intentional secured REST endpoint")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{ruleName}")
    public RuleConfigurationResponse getOne(@PathVariable String ruleName) {
        return configProvider
                .getConfiguration(ruleName)
                .map(this::toResponse)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Rule not found: " + ruleName));
    }

    @SuppressFBWarnings(
            value = "SPRING_ENDPOINT",
            justification = "Intentional secured REST endpoint")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{ruleName}")
    public RuleConfigurationResponse update(
            @PathVariable String ruleName,
            @Valid @RequestBody UpdateRuleConfigurationRequest request) {
        RuleConfigurationEntity entity =
                repository
                        .findById(ruleName)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Rule not found: " + ruleName));

        String parametersJson = serializeParameters(request.parameters());
        RuleConfigurationEntity updated =
                new RuleConfigurationEntity(
                        entity.getRuleName(),
                        request.enabled(),
                        request.score(),
                        parametersJson,
                        Instant.now());
        repository.save(updated);
        configProvider.evictCache();

        return toResponse(
                new RuleConfiguration(
                        updated.getRuleName(),
                        updated.isEnabled(),
                        updated.getScore(),
                        request.parameters()));
    }

    private RuleConfigurationResponse toResponse(RuleConfiguration config) {
        return new RuleConfigurationResponse(
                config.ruleName(), config.enabled(), config.score(), config.parameters(), null);
    }

    private String serializeParameters(java.util.Map<String, String> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid parameters format", e);
        }
    }
}
