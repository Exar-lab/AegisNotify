# ADR-0002: Resilience4j circuit breakers per provider channel

- **Status:** Accepted
- **Date:** 2026-08-01 (circuit breaker, PR #62, `d7a3d31`); 2026-09-05 (retry composition, PR #96,
  `d749c9c`)
- **Evidence:** `d7a3d31`, `ce9fc8a`, `504c972`, `d749c9c`, `ResilientNotificationProviderAdapter`,
  `aegis-notification-service/src/main/resources/application.yml`

## Context

The commit that introduced this decision (`d7a3d31`) records the problem: "Provider adapters had no
resilience layer: a single failing vendor call permanently failed the notification with no retry,
no failover, and no visibility into provider health."

## Decision

Wrap provider calls with Resilience4j. `ResilientNotificationProviderAdapter` is the sole
`NotificationProviderPort` implementation and holds one circuit breaker per channel, named
`email-provider`, `sms-provider`, `whatsapp-provider`, and `push-provider`.

Configuration (`application.yml`, `resilience4j.circuitbreaker.configs.default`):

| Setting | Value |
| --- | --- |
| Sliding window | count-based, 10 calls, minimum 10 calls |
| Failure-rate threshold | 50% |
| Slow-call threshold | 40% of calls slower than 2000 ms |
| Open-state wait | 60 s, automatic transition to half-open |
| Half-open permitted calls | 3 |
| Health indicator | registered |

The commit message states these values follow "issue #33's spec".

Retry is composed outside the breaker (`d749c9c`, "retry-outer, circuit-breaker-inner"). Failures
are classified by HTTP status (`ce9fc8a`): 429, 5xx, timeouts, and connection failures are
transient and retried; other 4xx responses are permanent. Only `TransientProviderDeliveryException`
is retried (3 attempts, 200 ms, exponential backoff, multiplier 2). `CallNotPermittedException` is
never retried, and a test asserts no extra primary invocations once the circuit is open.

The same breaker mechanism is reused for the aggregation summarizer under the instance name
`aggregation-agent`, with a 6000 ms slow-call threshold because that call has a 10 s timeout (see
[ADR-0006](0006-alternate-llm-summarizer-for-aggregation.md)).

## Alternatives considered

None are recorded in the repository. The project documentation names Resilience4j
(CircuitBreaker, Retry, TimeLimiter) as the chosen library without comparing others. Provider
adapters are not given a single shared breaker; the per-channel split is the decision, and its
rationale (a failing vendor for one channel should not block another) is inferred from
implementation.

## Consequences

### Positive

- A failing provider channel stops receiving traffic once the failure threshold is reached, and
  recovers through half-open probing.
- Breaker state is observable through `/actuator/health`, `/actuator/circuitbreakers`, and
  `resilience4j_circuitbreaker_*` metrics (verified by a Prometheus test in `d7a3d31`). The admin
  dashboard reads breaker status through `Resilience4jCircuitBreakerAdapter` (2026-10-12).
- Unit tests drive closed, open, half-open, and closed transitions with a real registry.

### Negative / trade-offs

- A count-based window of 10 means a breaker needs at least 10 calls before it can open, so low
  traffic channels react slowly (inferred from configuration).
- One breaker covers a channel, not an individual vendor account: primary and secondary accounts
  are not tracked separately.
- `docs/ARCHITECTURE.md` states these thresholds have not been validated against a real provider
  environment or under load.

## Related

- [ADR-0003](0003-provider-fallback-and-dead-letter-queue.md)
- Issues #33, #62, #72, PR #96.
