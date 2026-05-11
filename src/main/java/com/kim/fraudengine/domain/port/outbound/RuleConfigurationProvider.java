package com.kim.fraudengine.domain.port.outbound;

import com.kim.fraudengine.domain.model.RuleConfiguration;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for loading rule configurations. Implementations may read from static properties,
 * a database, or an external configuration service.
 */
public interface RuleConfigurationProvider {
    List<RuleConfiguration> getActiveRuleConfigurations();

    default Optional<RuleConfiguration> getConfiguration(String ruleName) {
        return getActiveRuleConfigurations().stream()
                .filter(c -> c.ruleName().equals(ruleName))
                .findFirst();
    }
}
