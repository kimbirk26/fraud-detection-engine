# ADR 0006: Kafka Partition Key Strategy

## Status

Accepted

## Context

The fraud detection engine uses Kafka for async transaction processing. The choice of partition key
directly impacts:

- **Ordering guarantees:** Kafka guarantees ordering only within a partition
- **Velocity rule accuracy:** The velocity rule counts recent transactions per customer; correct
  counts require serialized processing per customer
- **Consumer parallelism:** Partitions are the unit of parallelism for consumer groups

## Decision

Use `customerId` as the Kafka partition key for the `transactions.raw` topic.

This creates a dual-layer concurrency control:
1. **Kafka partition key (`customerId`):** Ensures all transactions for the same customer are routed
   to the same partition and consumed in order by a single consumer instance
2. **PostgreSQL advisory lock (`customerId`):** Serializes processing within the database transaction,
   protecting against race conditions from consumer rebalancing or duplicate delivery

## Consequences

**Positive:**
- Per-customer ordering within a partition ensures velocity counts are processed sequentially
- Advisory lock provides a safety net during consumer group rebalances
- Hot customers don't interfere with other customers' processing latency (partition isolation)
- Natural partitioning allows horizontal scaling up to partition count

**Negative:**
- Hot customers (very high transaction volume) can create partition hotspots
- Partition count caps maximum consumer parallelism
- If a customer's transactions span multiple partitions (e.g., after a topic repartition), ordering
  guarantees break (mitigated by advisory lock as fallback)

**Monitoring:**
- Monitor partition lag per consumer to detect hot partitions
- Alert on skewed partition sizes indicating customer concentration
