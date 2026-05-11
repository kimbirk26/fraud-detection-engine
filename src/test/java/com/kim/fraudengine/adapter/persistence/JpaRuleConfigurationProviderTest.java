package com.kim.fraudengine.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.fraudengine.adapter.persistence.entity.RuleConfigurationEntity;
import com.kim.fraudengine.domain.model.RuleConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaRuleConfigurationProviderTest {

    @Mock private RuleConfigurationJpaRepository repository;

    private JpaRuleConfigurationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JpaRuleConfigurationProvider(repository, new ObjectMapper());
    }

    @Test
    void shouldReturnAllConfigurations() {
        RuleConfigurationEntity entity =
                new RuleConfigurationEntity(
                        "VELOCITY_CHECK",
                        true,
                        40,
                        "{\"maxTransactions\":\"5\",\"windowMinutes\":\"10\"}",
                        Instant.now());

        when(repository.findAll()).thenReturn(List.of(entity));

        List<RuleConfiguration> configs = provider.getActiveRuleConfigurations();

        assertThat(configs).hasSize(1);
        RuleConfiguration config = configs.getFirst();
        assertThat(config.ruleName()).isEqualTo("VELOCITY_CHECK");
        assertThat(config.enabled()).isTrue();
        assertThat(config.score()).isEqualTo(40);
        assertThat(config.parameters()).containsEntry("maxTransactions", "5");
        assertThat(config.parameters()).containsEntry("windowMinutes", "10");
    }

    @Test
    void shouldCacheResults() {
        RuleConfigurationEntity entity =
                new RuleConfigurationEntity(
                        "VELOCITY_CHECK", true, 40, "{}", Instant.now());

        when(repository.findAll()).thenReturn(List.of(entity));

        provider.getActiveRuleConfigurations();
        provider.getActiveRuleConfigurations();
        provider.getActiveRuleConfigurations();

        verify(repository, times(1)).findAll();
    }

    @Test
    void shouldReloadAfterEviction() {
        RuleConfigurationEntity entity =
                new RuleConfigurationEntity(
                        "VELOCITY_CHECK", true, 40, "{}", Instant.now());

        when(repository.findAll()).thenReturn(List.of(entity));

        provider.getActiveRuleConfigurations();
        provider.evictCache();
        provider.getActiveRuleConfigurations();

        verify(repository, times(2)).findAll();
    }

    @Test
    void shouldReturnSingleConfigurationByName() {
        RuleConfigurationEntity entity1 =
                new RuleConfigurationEntity(
                        "VELOCITY_CHECK", true, 40, "{}", Instant.now());
        RuleConfigurationEntity entity2 =
                new RuleConfigurationEntity(
                        "BLACKLIST_MATCH", true, 50, "{}", Instant.now());

        when(repository.findAll()).thenReturn(List.of(entity1, entity2));

        Optional<RuleConfiguration> config = provider.getConfiguration("BLACKLIST_MATCH");

        assertThat(config).isPresent();
        assertThat(config.orElseThrow().ruleName()).isEqualTo("BLACKLIST_MATCH");
        assertThat(config.orElseThrow().score()).isEqualTo(50);
    }

    @Test
    void shouldReturnEmptyForUnknownRuleName() {
        when(repository.findAll()).thenReturn(List.of());

        Optional<RuleConfiguration> config = provider.getConfiguration("UNKNOWN_RULE");

        assertThat(config).isEmpty();
    }

    @Test
    void shouldHandleEmptyParametersJson() {
        RuleConfigurationEntity entity =
                new RuleConfigurationEntity(
                        "VELOCITY_CHECK", true, 40, "{}", Instant.now());

        when(repository.findAll()).thenReturn(List.of(entity));

        List<RuleConfiguration> configs = provider.getActiveRuleConfigurations();

        assertThat(configs.getFirst().parameters()).isEmpty();
    }
}
