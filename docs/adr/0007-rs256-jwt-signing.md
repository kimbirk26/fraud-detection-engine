# ADR 0007: RS256 JWT Signing

## Status

Accepted

## Context

The fraud detection engine previously used HS256 (HMAC-SHA256) for JWT signing (see [ADR-0002](0002-hs256-jwt-signing.md)).
ADR-0002 identified RS256 as the evolution path when the system moves toward a multi-service architecture.

HS256 uses a single shared secret for both signing and verification. This means any service that needs
to verify tokens must also possess the signing secret, creating a security risk in distributed deployments.

## Decision

We migrate JWT signing from HS256 to RS256 (RSA with SHA-256) using a 2048-bit RSA key pair:

- **Private key** (PKCS#8 PEM format): used by the fraud detection engine to sign tokens
- **Public key** (X.509 PEM format): used to verify tokens, can be safely shared with other services

Keys are loaded from environment variables (`JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`), sourced from
Kubernetes secrets or AWS Secrets Manager via ExternalSecret.

For local development and tests, a dedicated RSA key pair is embedded in `application-local.yml`
and `src/test/resources/application.properties`.

## Consequences

**Positive:**
- Separation of signing and verification capabilities — other services can verify tokens
  using only the public key, without access to the signing secret
- Enables future multi-service architecture without sharing sensitive key material
- Standard RSA key pair management aligns with industry practices

**Negative:**
- Two keys to manage and rotate instead of one
- Key rotation requires updating both private and public keys
- RSA signing/verification is slower than HMAC (negligible at current throughput)
- PEM-encoded keys are larger than a simple HMAC secret string
