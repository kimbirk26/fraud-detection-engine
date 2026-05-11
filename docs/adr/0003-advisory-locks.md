# ADR 0003: PostgreSQL Advisory Locks for Customer-Level Serialization

## Status

Accepted

## Context

The velocity rule counts recent transactions for a customer and compares against a threshold.
Without serialization, two concurrent transactions for the same customer could both read the same
count, both pass the check, and both be saved — resulting in a missed velocity violation.

Options considered:
- **Distributed locks (Redis/Zookeeper):** Proven but adds infrastructure, operational burden, and a
  new failure mode
- **Optimistic locking with retry:** Complex retry logic, potential starvation under high contention
- **PostgreSQL advisory locks:** Zero additional infrastructure; transaction-scoped with auto-release;
  DB is already the source of truth

## Decision

Use PostgreSQL transaction-scoped advisory locks (`pg_advisory_xact_lock`) keyed on a hash of the
customer ID. The lock is acquired at the start of transaction processing and released automatically
when the transaction commits or rolls back.

## Consequences

**Positive:**
- Zero additional infrastructure — PostgreSQL is already the persistence layer
- Transaction-scoped locks auto-release on commit/rollback; no risk of orphaned locks
- Serializes per-customer processing without blocking unrelated customers
- The database is already the source of truth for transaction counts

**Negative:**
- Couples the serialization mechanism to PostgreSQL (less portable)
- Advisory locks are invisible to standard monitoring tools unless explicitly queried
- If the system scales beyond a single database, advisory locks won't work across shards

**Upgrade path:** For multi-database deployments, replace with Redis-based distributed locks
(e.g., Redisson) behind the existing `TransactionHistoryRepository.lockCustomer()` port method.
