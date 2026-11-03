# Architecture Decision Records

This directory records the key architecture decisions behind AegisNotify. Each record states the
context, the decision, the alternatives that are evidenced in the repository, and the
consequences.

The records were written retrospectively, from source code, configuration, commit messages, and
existing documentation (issue #198). Dates are the dates the change landed in git history.
Where a rationale is not stated in the repository, the record labels it "inferred from
implementation".

New decisions start from [`0000-template.md`](0000-template.md). Number records sequentially and
never reuse a number; change a record's status instead of deleting it.

## Index

| ADR | Title | Status | Date |
| --- | --- | --- | --- |
| [0001](0001-transactional-outbox.md) | Transactional Outbox for notification publication | Accepted | 2026-06-20 |
| [0002](0002-resilience4j-circuit-breakers-per-provider.md) | Resilience4j circuit breakers per provider channel | Accepted | 2026-08-01 |
| [0003](0003-provider-fallback-and-dead-letter-queue.md) | Provider fallback and dead-letter queue | Accepted | 2026-08-01 |
| [0004](0004-hexagonal-layering-enforced-by-archunit.md) | Hexagonal layering enforced by ArchUnit | Accepted | 2026-06-20 |
| [0005](0005-keycloak-jwt-scopes-at-the-gateway.md) | Keycloak JWT scopes enforced at the gateway and the services | Accepted | 2026-08-12 |
| [0006](0006-alternate-llm-summarizer-for-aggregation.md) | Config-selectable LLM summarizer (Anthropic or Groq) for aggregation | Accepted | 2026-11-02 |

See also the [architecture overview](../ARCHITECTURE.md).
