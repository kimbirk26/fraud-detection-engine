# ADR 0001: Hexagonal Architecture

## Status

Accepted

## Context

The fraud detection engine needs a clear architectural pattern that supports:
- Independent testing of business rules without Spring context
- Clean separation between domain logic and infrastructure concerns
- Easy substitution of external adapters (e.g., swapping Kafka for RabbitMQ, PostgreSQL for another store)
- The domain rules being the competitive differentiator — they must be isolated and testable

Traditional layered MVC architecture couples controllers to services to repositories, making it
difficult to test rules in isolation and creating dependency arrows pointing inward from the domain.

## Decision

We adopt hexagonal architecture (ports and adapters) with the following package structure:

- `domain/` — Pure business logic: models, rules, ports (interfaces), exceptions. No framework dependencies.
- `application/` — Use-case orchestration services that coordinate domain objects and ports.
- `adapter/` — Inbound (REST, Kafka consumer) and outbound (JPA, Kafka publisher) adapters implementing ports.
- `infrastructure/` — Cross-cutting concerns: configuration, security, logging.

Dependencies point inward: adapters depend on ports; ports are defined in the domain layer.

## Consequences

**Positive:**
- Domain rules can be unit-tested with zero Spring context, keeping the feedback loop fast
- Adding a new adapter (e.g., gRPC inbound) requires no changes to domain code
- Clear boundaries make it easy to reason about change impact

**Negative:**
- More interfaces (ports) than a simple layered approach
- Mapping between domain models and adapter-specific DTOs introduces boilerplate
- Developers unfamiliar with the pattern need onboarding
