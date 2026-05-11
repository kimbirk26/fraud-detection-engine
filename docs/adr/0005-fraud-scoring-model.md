# ADR 0005: Cumulative Fraud Scoring Model

## Status

Accepted

## Context

The initial fraud detection implementation used binary rule evaluation: each rule either triggers
(creating an alert) or passes. This approach has limitations:

- Low-confidence signals (e.g., out-of-hours transactions) generate noise as standalone alerts
- No way to express "suspicious when combined with other factors"
- Analysts receive too many low-value alerts, reducing the signal-to-noise ratio
- No quantitative measure of fraud likelihood for prioritization

## Decision

Evolve to a cumulative scoring model where:

1. Each rule contributes a weighted score when triggered (e.g., blacklist=50, velocity=40,
   out-of-hours=15)
2. Scores from all triggered rules are summed into a `totalScore`
3. An alert is only created when `totalScore >= scoreThreshold` (default: 40)
4. Below-threshold evaluations are logged at DEBUG level for tuning

The threshold and per-rule scores are configurable via `application.yml`.

## Consequences

**Positive:**
- Reduces false positive alerts: a single low-confidence rule no longer generates an alert
- Enables compound risk detection: out-of-hours + foreign-country together exceed the threshold
- Provides a quantitative score for analyst prioritization (higher score = investigate first)
- Scores are tunable without code changes (configuration-driven)
- Maintains backward compatibility: existing high-confidence rules (blacklist=50) still exceed the
  threshold individually

**Negative:**
- Slightly more complex mental model for understanding when an alert fires
- Requires score calibration: initial values are estimates that need tuning with production data
- Below-threshold triggers are invisible in the alert table (only in debug logs)

**Future evolution:**
- ML-based score calibration using false-positive feedback loops
- Per-customer risk profiles adjusting the threshold dynamically
- Score decay over time for repeated patterns
