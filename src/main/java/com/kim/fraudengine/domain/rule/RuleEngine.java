package com.kim.fraudengine.domain.rule;

import com.kim.fraudengine.domain.model.EvaluationOutcome;
import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.TransactionContext;
import java.util.List;

public class RuleEngine {

    private final List<FraudRule> rules;
    private final int scoreThreshold;

    public RuleEngine(List<FraudRule> rules, int scoreThreshold) {
        this.rules = List.copyOf(rules);
        this.scoreThreshold = scoreThreshold;
    }

    public EvaluationOutcome evaluate(TransactionContext context) {
        List<FraudRule> activeRules = rules.stream().filter(FraudRule::isEnabled).toList();
        List<RuleResult> allResults =
                activeRules.stream().map(rule -> rule.evaluate(context)).toList();
        List<RuleResult> triggered =
                allResults.stream().filter(RuleResult::triggered).toList();
        int totalScore = triggered.stream().mapToInt(RuleResult::score).sum();
        return new EvaluationOutcome(
                allResults, triggered, totalScore, totalScore >= scoreThreshold);
    }
}
