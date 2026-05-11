package com.kim.fraudengine.adapter.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.fraudengine.adapter.persistence.entity.RuleConfigurationEntity;
import com.kim.fraudengine.domain.model.RuleConfiguration;
import com.kim.fraudengine.domain.port.outbound.RuleConfigurationProvider;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JpaRuleConfigurationProvider implements RuleConfigurationProvider {

    private static final String CACHE_KEY = "all";
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final RuleConfigurationJpaRepository repository;
    private final ObjectMapper objectMapper;
    private final Cache<String, List<RuleConfiguration>> cache;

    public JpaRuleConfigurationProvider(
            RuleConfigurationJpaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).build();
    }

    @Override
    public List<RuleConfiguration> getActiveRuleConfigurations() {
        return cache.get(CACHE_KEY, key -> loadAll());
    }

    public void evictCache() {
        cache.invalidateAll();
    }

    private List<RuleConfiguration> loadAll() {
        return repository.findAll().stream().map(this::toRuleConfiguration).toList();
    }

    private RuleConfiguration toRuleConfiguration(RuleConfigurationEntity entity) {
        Map<String, String> params = parseParameters(entity.getParameters());
        return new RuleConfiguration(
                entity.getRuleName(), entity.isEnabled(), entity.getScore(), params);
    }

    private Map<String, String> parseParameters(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse rule parameters JSON", e);
        }
    }
}
