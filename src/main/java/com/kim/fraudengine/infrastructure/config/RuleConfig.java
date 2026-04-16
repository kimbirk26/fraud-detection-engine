package com.kim.fraudengine.infrastructure.config;

import com.kim.fraudengine.domain.rule.AmountThresholdRule;
import com.kim.fraudengine.domain.rule.BlacklistRule;
import com.kim.fraudengine.domain.rule.ForeignCountryRule;
import com.kim.fraudengine.domain.rule.OutOfHoursRule;
import com.kim.fraudengine.domain.rule.RuleEngine;
import com.kim.fraudengine.domain.rule.VelocityRule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * Wires fraud rules from application.yml into the RuleEngine. Adding a new rule = one new @Bean
 * here. Nothing else changes. Open/Closed Principle
 */
@Configuration
public class RuleConfig {

    @Bean
    public AmountThresholdRule amountThresholdRule(
            @Value("${app.rules.amount.high-threshold}") BigDecimal high,
            @Value("${app.rules.amount.medium-threshold}") BigDecimal medium) {
        return new AmountThresholdRule(high, medium);
    }

    @Bean
    public BlacklistRule blacklistRule(
            @Value("${app.rules.blacklist.merchant-ids}") Set<String> merchantIds) {
        return new BlacklistRule(merchantIds);
    }

    @Bean
    public OutOfHoursRule outOfHoursRule(
            @Value("${app.rules.out-of-hours.start}") int start,
            @Value("${app.rules.out-of-hours.end}") int end,
            @Value("${app.rules.out-of-hours.timezone}") String timezone) {
        return new OutOfHoursRule(start, end, ZoneId.of(timezone));
    }

    @Bean
    public ForeignCountryRule foreignCountryRule(
            @Value("${app.rules.foreign-country.home-country-code}") String homeCountryCode,
            @Value("${app.rules.foreign-country.minimum-amount}") BigDecimal minimumAmount) {
        return new ForeignCountryRule(homeCountryCode, minimumAmount);
    }

    @Bean
    public VelocityRule velocityRule(
            @Value("${app.rules.velocity.max-transactions}") int maxTransactions,
            @Value("${app.rules.velocity.window-minutes}") int windowMinutes) {
        return new VelocityRule(maxTransactions, windowMinutes);
    }

    @Bean
    public RuleEngine ruleEngine(
            AmountThresholdRule amountRule,
            BlacklistRule blacklistRule,
            OutOfHoursRule outOfHoursRule,
            ForeignCountryRule foreignCountryRule,
            VelocityRule velocityRule) {

        return new RuleEngine(
                List.of(amountRule, blacklistRule, outOfHoursRule, foreignCountryRule, velocityRule));
    }
}
