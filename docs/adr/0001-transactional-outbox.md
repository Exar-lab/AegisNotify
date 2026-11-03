# ADR-0001: Transactional Outbox for notification publication

- **Status:** Accepted
- **Date:** 2026-06-20 (outbox write path, `26fe95c`); 2026-09-01 (scheduled relay, `4ece5bf`)
- **Evidence:** `26fe95c`, `4ece5bf`, `PublishOutboxEventService`, `OutboxWorkerScheduler`,
  `docs/ARCHITECTURE.md`

## Context

`POST /api/v1/notifications` must return `202 Accepted` without waiting for an external provider
(`docs/ARCHITECTURE.md`, "Notification processing flow"). The service therefore has to persist the
notification and hand it to a broker (Kafka) afterwards. Writing to the database and publishing to
Kafka are two separate systems, so a naive "save, then publish" can lose a message if the process
fails between the two steps (inferred from implementation).

## Decision

Use the Transactional Outbox pattern. The initial ingress commit (`26fe95c`) persists the
notification, its audit log entry, and an outbox event "in a single transaction" and returns
`202` with a UUID. A separate relay then publishes outbox events to Kafka.

- `PublishOutboxEventService.publishPending()` reads pending outbox events and processes each one in
  its own transaction (`PublishOutboxEventTransactions`). A failure on one event rolls back only
  that event and leaves it `UNPROCESSED` for retry; earlier events in the batch stay committed.
- `OutboxWorkerScheduler`, gated by `notification.kafka.relay.enabled`, calls `publishPending()` on
  a schedule (`poll-interval` default `PT5S`). The commit that added it (`4ece5bf`) states that it
  makes the pattern "no longer dormant".
- Priority events are mapped to the `high-priority-topic`, `medium-priority-topic`, and
  `low-priority-topic` topics.
- The `outbox_events` table is created by Flyway; JPA runs with `ddl-auto: validate`.

## Alternatives considered

No alternative is recorded in the repository. The project documentation (`CLAUDE.md`,
`docs/ARCHITECTURE.md`) names the outbox as the chosen pattern without comparing it to others.

## Consequences

### Positive

- The notification and its outbox event commit atomically, so an accepted request cannot be lost
  between the database and the broker.
- Delivery to the broker is at-least-once; a failed publish is retried on the next poll.
- The HTTP request path does not depend on Kafka availability (inferred from implementation).

### Negative / trade-offs

- Consumers can receive an event more than once and must tolerate duplicates (inferred from
  at-least-once publication).
- Publication latency is bounded below by the relay poll interval.
- The relay is disabled by default (`NOTIFICATION_KAFKA_RELAY_ENABLED=false`). Between `2026-06-20`
  and `2026-09-01` the outbox had no scheduled publisher (`4ece5bf`).

## Related

- [ADR-0003](0003-provider-fallback-and-dead-letter-queue.md) for what happens after the event is
  consumed.
- [ADR-0006](0006-alternate-llm-summarizer-for-aggregation.md): the relay also hosts the
  aggregation hold guard.
- Issue #86 (aggregation, relay prerequisite), issue #32.
