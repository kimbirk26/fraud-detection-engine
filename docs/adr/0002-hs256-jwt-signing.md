# ADR 0002: HS256 JWT Signing

## Status

Superseded by [ADR-0007](0007-rs256-jwt-signing.md)

## Context

The fraud detection engine requires JWT-based authentication for its REST API. The choice of
signing algorithm affects key management complexity, token verification performance, and
multi-service scalability.

Options considered:
- **HS256** (HMAC with SHA-256): Symmetric — single shared secret for both signing and verification
- **RS256** (RSA with SHA-256): Asymmetric — private key signs, public key verifies

## Decision

We use HS256 with a single shared secret stored in AWS Secrets Manager.

Rationale:
- This is a single-service deployment; there is no need to distribute a public key to multiple verifiers
- One secret to manage vs. a key pair (private key rotation, certificate lifecycle)
- Faster signing and verification than RSA at equivalent security levels
- Simpler operational model for the current 2-6 pod deployment

## Consequences

**Positive:**
- Single secret to manage and rotate
- Faster token operations
- Simpler key management in Kubernetes (one ExternalSecret reference)

**Negative:**
- If the service evolves to a multi-service architecture where other services need to verify tokens
  independently, HS256 would require sharing the secret with all verifiers (security risk)
- No separation between "who can sign" and "who can verify"

**Evolution path:** When/if the system moves to a multi-service architecture (e.g., a separate
API gateway, multiple microservices), migrate to RS256. The `JwtService` abstraction makes this a
localized change.
