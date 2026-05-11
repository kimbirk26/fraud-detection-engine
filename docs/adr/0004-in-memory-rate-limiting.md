# ADR 0004: In-Memory Rate Limiting

## Status

Accepted

## Context

The authentication endpoint (`POST /api/v1/auth/login`) needs rate limiting to mitigate brute-force
attacks. The rate limiter must track per-IP token buckets.

Options considered:
- **Redis via bucket4j-redis:** Cluster-wide enforcement; accurate across all pods
- **In-memory ConcurrentHashMap:** Zero infrastructure; per-pod limits

## Decision

Use in-memory `ConcurrentHashMap` with bucket4j-core for per-IP rate limiting.

Rationale:
- Acceptable for the current 2-6 pod deployment: an attacker gets at most N * capacity attempts
  (where N = number of pods), which is still within acceptable risk for the configured capacity
- Zero additional infrastructure dependency
- Simpler failure mode — rate limiting degrades gracefully (limits are per-pod rather than lost entirely)
- The authentication endpoint already uses Argon2 hashing which is intentionally slow, providing
  additional brute-force resistance

**Important:** In an N-pod deployment, an attacker can make up to N * capacity attempts before being
fully blocked. For the default configuration (capacity=5, 2-6 pods), this means 10-30 attempts.
Combined with Argon2's computational cost, this is an acceptable risk.

## Consequences

**Positive:**
- No Redis dependency for rate limiting
- No network round-trips for bucket checks
- Survives Redis outages (rate limiting never fails open)

**Negative:**
- Limits are per-pod, not cluster-wide
- Pod restarts reset all buckets (attacker gets a fresh start)
- Memory usage scales with unique IPs (mitigated by TTL-based eviction and max-tracked-clients cap)

**Upgrade path:** Replace `ConcurrentHashMap` with `bucket4j-redis` when:
- The pod count exceeds 6 and the multiplied capacity becomes unacceptable
- Regulatory requirements mandate cluster-wide enforcement
- Redis is already in the stack for other reasons (e.g., caching)

See: `AuthRateLimitFilter.java` — the `ConcurrentHashMap` field is the single point of change.
