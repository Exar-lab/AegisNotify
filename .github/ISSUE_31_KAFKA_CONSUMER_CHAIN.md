# Kafka consumer delivery chain

Issue #31 is delivered through five focused pull requests. Each child stays within a 400-line review budget and targets its immediate parent branch.

## Review order

| Position | Branch | Scope | Budget |
|---|---|---|---:|
| 1 | `feat/31-notification-event-contract` | Application event contract and processing behavior | 149 |
| 2 | `feat/31-kafka-topics-config` | Typed topic properties and topic provisioning | 236 |
| 3 | `feat/31-kafka-metrics` | Consumer metrics and Prometheus registry | 73 |
| 4 | `feat/31-kafka-consumer-config` | Deserialization, retry, acknowledgment, and DLT recovery | 399 |
| 5 | `feat/31-kafka-listener-adapter` | Listener adapter and integration coverage | 365 |

## Merge policy

1. Review and integrate child pull requests in order.
2. Keep this tracker pull request in draft while any child remains open.
3. Merge only this tracker branch into `main` after the complete chain is integrated and verified.

## Verification

- Deterministic notification-service tests must pass for every child.
- The Testcontainers scenario must run in Docker-capable CI before final integration.
- Every child must link issue #31 and include its dependency context.
