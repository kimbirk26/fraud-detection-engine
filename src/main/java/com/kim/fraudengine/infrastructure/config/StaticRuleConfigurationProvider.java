package com.kim.fraudengine.infrastructure.config;

import com.kim.fraudengine.domain.model.RuleConfiguration;
import com.kim.fraudengine.domain.port.outbound.RuleConfigurationProvider;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default implementation reading rule configurations from Spring {@code @ConfigurationProperties}.
 * Annotated with {@code @ConditionalOnMissingBean} so that a database-backed or remote
 * implementation can replace this simply by declaring its own {@code RuleConfigurationProvider} bean.
 */
@Component
@ConditionalOnMissingBean(RuleConfigurationProvider.class)
public class StaticRuleConfigurationProvider implements RuleConfigurationProvider {

    private final int amountHighScore;
    private final int amountMediumScore;
    private final int blacklistScore;
    private final int velocityScore;
    private final int foreignCountryScore;
    private final int outOfHoursScore;

    public StaticRuleConfigurationProvider(
            @Value("${app.rules.amount.high-score:40}") int amountHighScore,
            @Value("${app.rules.amount.medium-score:20}") int amountMediumScore,
            @Value("${app.rules.blacklist.score:50}") int blacklistScore,
            @Value("${app.rules.velocity.score:40}") int velocityScore,
            @Value("${app.rules.foreign-country.score:20}") int foreignCountryScore,
            @Value("${app.rules.out-of-hours.score:15}") int outOfHoursScore) {
        this.amountHighScore = amountHighScore;
        this.amountMediumScore = amountMediumScore;
        this.blacklistScore = blacklistScore;
        this.velocityScore = velocityScore;
        this.foreignCountryScore = foreignCountryScore;
        this.outOfHoursScore = outOfHoursScore;
    }

    @Override
    public List<RuleConfiguration> getActiveRuleConfigurations() {
        return List.of(
                new RuleConfiguration(
                        "AMOUNT_THRESHOLD",
                        true,
                        amountHighScore,
                        Map.of("highScore", String.valueOf(amountHighScore),
                                "mediumScore", String.valueOf(amountMediumScore))),
                new RuleConfiguration(
                        "BLACKLIST_MATCH", true, blacklistScore, Map.of()),
                new RuleConfiguration(
                        "VELOCITY_CHECK", true, velocityScore, Map.of()),
                new RuleConfiguration(
                        "FOREIGN_COUNTRY", true, foreignCountryScore, Map.of()),
                new RuleConfiguration(
                        "OUT_OF_HOURS", true, outOfHoursScore, Map.of()));
    }
}
