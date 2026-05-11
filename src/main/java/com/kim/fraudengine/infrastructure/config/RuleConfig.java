package com.kim.fraudengine.infrastructure.config;

import com.kim.fraudengine.domain.port.outbound.RuleConfigurationProvider;
import com.kim.fraudengine.domain.rule.AmountThresholdRule;
import com.kim.fraudengine.domain.rule.BlacklistRule;
import com.kim.fraudengine.domain.rule.ForeignCountryRule;
import com.kim.fraudengine.domain.rule.OutOfHoursRule;
import com.kim.fraudengine.domain.rule.RuleEngine;
import com.kim.fraudengine.domain.rule.VelocityRule;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires fraud rules from application.yml into the RuleEngine. Adding a new rule = one new @Bean
 * here. Nothing else changes. Open/Closed Principle.
 *
 * <p>Each rule receives both a {@code RuleConfigurationProvider} (for dynamic DB-backed config) and
 * {@code @Value} defaults (used as fallbacks when no DB row exists).
 */
@Configuration
public class RuleConfig {

    @Bean
    public AmountThresholdRule amountThresholdRule(
            RuleConfigurationProvider configProvider,
            @Value("${app.rules.amount.high-threshold}") BigDecimal high,
            @Value("${app.rules.amount.medium-threshold}") BigDecimal medium,
            @Value("${app.rules.amount.high-score:40}") int highScore,
            @Value("${app.rules.amount.medium-score:20}") int mediumScore) {
        return new AmountThresholdRule(configProvider, high, medium, highScore, mediumScore);
    }

    @Bean
    public BlacklistRule blacklistRule(
            RuleConfigurationProvider configProvider,
            @Value("${app.rules.blacklist.merchant-ids}") Set<String> merchantIds,
            @Value("${app.rules.blacklist.score:50}") int score) {
        return new BlacklistRule(configProvider, merchantIds, score);
    }

    @Bean
    public OutOfHoursRule outOfHoursRule(
            RuleConfigurationProvider configProvider,
            @Value("${app.rules.out-of-hours.start}") int start,
            @Value("${app.rules.out-of-hours.end}") int end,
            @Value("${app.rules.out-of-hours.timezone}") String timezone,
            @Value("${app.rules.out-of-hours.score:15}") int score) {
        return new OutOfHoursRule(configProvider, start, end, ZoneId.of(timezone), score);
    }

    @Bean
    public ForeignCountryRule foreignCountryRule(
            RuleConfigurationProvider configProvider,
            @Value("${app.rules.foreign-country.home-country-code}") String homeCountryCode,
            @Value("${app.rules.foreign-country.minimum-amount}") BigDecimal minimumAmount,
            @Value("${app.rules.foreign-country.score:20}") int score) {
        return new ForeignCountryRule(configProvider, homeCountryCode, minimumAmount, score);
    }

    @Bean
    public VelocityRule velocityRule(
            RuleConfigurationProvider configProvider,
            @Value("${app.rules.velocity.max-transactions}") int maxTransactions,
            @Value("${app.rules.velocity.window-minutes}") int windowMinutes,
            @Value("${app.rules.velocity.score:40}") int score) {
        return new VelocityRule(configProvider, maxTransactions, windowMinutes, score);
    }

    @Bean
    public RuleEngine ruleEngine(
            AmountThresholdRule amountRule,
            BlacklistRule blacklistRule,
            OutOfHoursRule outOfHoursRule,
            ForeignCountryRule foreignCountryRule,
            VelocityRule velocityRule,
            @Value("${app.rules.scoring.threshold:40}") int scoreThreshold) {

        return new RuleEngine(
                List.of(
                        amountRule,
                        blacklistRule,
                        outOfHoursRule,
                        foreignCountryRule,
                        velocityRule),
                scoreThreshold);
    }
}
