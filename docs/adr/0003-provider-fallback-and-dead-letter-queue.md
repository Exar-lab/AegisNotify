# ADR-0003: Provider fallback and dead-letter queue

- **Status:** Accepted
- **Date:** 2026-08-01 (fallback, `d7a3d31`); 2026-10-02 (DLQ adapter, `2c77113`)
- **Evidence:** `d7a3d31`, `2c77113`, `ResilientNotificationProviderAdapter`,
  `KafkaDeadLetterQueueAdapter`, `DeadLetterQueuePort`, `application.yml`

## Context

When the primary provider for a channel fails or its circuit is open, the notification would
otherwise be lost (see [ADR-0002](0002-resilience4j-circuit-breakers-per-provider.md)). A message
that fails on both providers still needs to be preserved for manual intervention
(`CLAUDE.md`, "Dead Letter Queue").

## Decision

Two stages, both in the notification service:

1. **Provider fallback.** On every primary failure, whether the call was attempted and failed or
   rejected by an open breaker, `ResilientNotificationProviderAdapter.failOver` immediately calls
   the secondary provider configured for that channel. Success yields `SENT_VIA_FALLBACK` and
   increments `aegisnotify.fallback.transmissions`. Failure of both yields `FAILED_CRITICAL`.
   Every attempt is recorded as a `DeliveryAttempt` with role `PRIMARY` or `FALLBACK`.
2. **Dead-letter queue.** `FAILED_CRITICAL` is routed to the DLQ through `DeadLetterQueuePort`,
   implemented by `KafkaDeadLetterQueueAdapter` (default topic `notifications-dlq`,
   `notification.kafka.topics.dlq`). The notification ID and failure reason are Kafka headers. The
   adapter reuses the existing `acks=all` idempotent producer. It throws on failure instead of
   swallowing the error: `SendToDlqService` is transactional, so a failed publish rolls back the
   `FAILED_CRITICAL` status change rather than claiming a delivery that did not occur
   (`2c77113`).

Separately, the Kafka consumer retries a failing record twice with a one-second backoff and then
publishes to a per-source `<topic>-dlt` topic (`docs/ARCHITECTURE.md`).

An unconfigured secondary "degrades to always-FAILED rather than blocking startup" (`d7a3d31`).

## Alternatives considered

None are recorded in the repository. `2c77113` notes that the DLQ adapter reuses the existing
producer "rather than standing up a second one" because there is "no isolation benefit to
duplicating it"; that is an implementation choice, not a rejected architecture.

## Consequences

### Positive

- A single vendor outage on a channel can be absorbed by a second account without failing the
  notification.
- The final state distinguishes `SENT` from `SENT_VIA_FALLBACK`, and the attempt history shows
  which provider handled each try.
- Unrecoverable messages are retained on a topic for manual handling.

### Negative / trade-offs

- Secondary accounts are optional; with none configured, a primary failure goes straight to
  `FAILED_CRITICAL`.
- `2c77113` records that, before it landed, no production `DeadLetterQueuePort` implementation
  existed and the service could not start. The DLQ path is therefore the newest part of this
  decision.
- `docs/ARCHITECTURE.md` states that failover is covered by unit tests but is not a validated
  production guarantee.
- A two-hop failure path exists (DLQ topic and per-source `-dlt` topic); operators need to know both.
  This is inferred from the two code paths.

## Related

- [ADR-0001](0001-transactional-outbox.md), [ADR-0002](0002-resilience4j-circuit-breakers-per-provider.md)
- Issue #30 (DLQ), PR #62.
