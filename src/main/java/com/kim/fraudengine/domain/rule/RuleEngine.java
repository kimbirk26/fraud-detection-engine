package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.TransactionContext;
import java.util.List;

public class RuleEngine {

    private final List<FraudRule> rules;

    public RuleEngine(List<FraudRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<RuleResult> evaluate(TransactionContext context) {
        return rules.stream()
                .map(rule -> rule.evaluate(context))
                .filter(RuleResult::triggered)
                .toList();
    }
}
