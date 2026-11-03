# Glossary

[Manual index](README.md) | Previous: [Roles and permissions](roles-and-permissions.md) | Next: [Troubleshooting](troubleshooting.md)

Terms are grouped by topic. Labels in `MONOSPACE` appear exactly as shown in the console. States
and values were checked against the platform source code.

## Notification states

Every notification has one current status. A normal delivery moves forward through the states:

```
PENDING -> QUEUED -> PROCESSING -> SENT
                                -> SENT_VIA_FALLBACK
                                -> FAILED
                                -> FAILED_CRITICAL
```

| State | Meaning |
| --- | --- |
| `PENDING` | The request was accepted and recorded. It has not yet been handed to the message broker for processing. |
| `QUEUED` | The request was published to the message broker (to the queue for its priority) and is waiting to be picked up. |
| `PROCESSING` | The platform picked the message up, is building the message from its template, and is sending it through a provider. |
| `SENT` | The primary provider delivered the message. Final state. |
| `SENT_VIA_FALLBACK` | The primary provider could not deliver, and the fallback provider did. The message was delivered. Final state. |
| `FAILED` | Delivery failed. The platform allows a failed notification to be retried through its API. The console has no retry button. |
| `FAILED_CRITICAL` | Both the primary and the fallback provider failed. The message was not delivered and was routed to the dead-letter queue for manual follow-up. Final state. |
| `CANCELLED` | The notification was cancelled before it was processed. The platform only allows cancelling a notification that is `PENDING` or `QUEUED`. The Notifications filter offers this state although it is not part of the normal flow. |

In the notification timeline you may also see two entries that are not states of the notification
itself but records of a failed attempt:

| Entry | Meaning |
| --- | --- |
| `PROVIDER A FAIL` | The primary provider failed on an attempt. |
| `PROVIDER B FAIL` | The fallback provider failed on an attempt. |

## Channels

A channel is the way a message reaches its recipient.

| Channel | Recipient is |
| --- | --- |
| `EMAIL` | An email address. |
| `SMS` | A phone number in international (E.164) format, for example `+34600000000`. |
| `WHATSAPP` | A phone number in international (E.164) format. |
| `PUSH` | A device or push-notification target. |

## Priorities

| Priority | Meaning |
| --- | --- |
| `HIGH` | Routed to the high-priority queue. |
| `MEDIUM` | Routed to the medium-priority queue. |
| `LOW` | Routed to the low-priority queue. |

The platform uses a separate queue for each priority (`high-priority-topic`,
`medium-priority-topic`, `low-priority-topic` by default). This manual does not describe how fast
each queue is served.

## Circuit-breaker states

A **circuit breaker** protects the platform from a provider that is failing. It works like an
electrical breaker. While the provider behaves, the circuit is closed and traffic flows. If too many
calls fail or are slow, the breaker trips, calls to that provider stop for a while, and messages
go to the fallback provider instead. After a waiting period it lets a few test calls through to see
whether the provider has recovered.

| State | Meaning for you |
| --- | --- |
| `CLOSED` | Normal. Calls go to the provider. Shown in green. |
| `OPEN` | The breaker has tripped. Calls to the primary provider are blocked and messages are routed to the fallback provider. Shown in red. |
| `HALF_OPEN` | Recovery test. After the waiting period the breaker allows a small number of test calls. If they succeed it returns to `CLOSED`, otherwise back to `OPEN`. Shown in amber. |
| `FORCED_OPEN` | The breaker was forced open by an operator or by configuration. Calls are blocked and it does not recover on its own. Shown in red. |

The console can also show these labels, which are shown in grey:

| State | Meaning |
| --- | --- |
| `NOT_PROTECTED` | Shown for fallback providers, which have no circuit breaker of their own (displayed as `NO CIRCUIT` on the Providers cards). |
| `DISABLED` | The circuit breaker is switched off. |
| `METRICS_ONLY` | The breaker only records statistics and does not block calls. |
| `UNKNOWN` | The state could not be read. |

Configured thresholds for the delivery providers (platform defaults):

| Setting | Value |
| --- | --- |
| Failure-rate threshold | 50% of the last 10 calls (needs at least 10 calls before it can trip) |
| Slow-call threshold | 40% of calls slower than 2 seconds |
| Time in `OPEN` before testing | 60 seconds, after which the breaker moves to `HALF_OPEN` automatically |
| Test calls in `HALF_OPEN` | 3 |

These values are set by your administrators and can differ in your environment.

## Delivery and failure terms

| Term | Meaning |
| --- | --- |
| **Primary provider** | The provider tried first for a channel. |
| **Fallback provider** (secondary) | The provider used when the primary is blocked or fails. Fallback does not happen as a separate step you control. It is automatic. |
| **Fallback activation** | One delivery that went to the fallback provider. Counted on the Dashboard and Metrics screens. |
| **Retry** | The platform automatically repeats a provider call that failed with a temporary error a limited number of times (3 attempts in total, with a short, growing delay) before giving up on the primary provider. Retries appear as extra `PRIMARY` rows in Provider attempts. |
| `CIRCUIT_OPEN` | A result code in Provider attempts. The call was not made at all because the provider's circuit breaker was open. |
| **Provider attempt** | One recorded call to a provider, shown on the notification detail view. |
| **DLQ (dead-letter queue)** | A holding queue for messages that could not be delivered or processed after all attempts. Messages in it need manual follow-up. The **DLQ** figure on the Metrics screen counts them. |
| **Outbox** | How the platform makes sure a request is never lost. The request and a record to publish it are saved together in one step. A background worker then reads those records and publishes them to the message broker. This is why a notification can stay `PENDING` for a short time after it is accepted. |
| **Message broker / Kafka** | The system that carries notifications from the part that accepts them to the part that delivers them. Shown as "Kafka" on the Metrics screen. |
| **Consumer** | The part of the platform that picks messages up from the broker and delivers them. |
| **Correlation ID** | An identifier used to follow one request across the platform's services. Quote it when you ask for support. In the current backend its value equals the notification ID. |
| **Notification ID** | The unique identifier of a notification, assigned when the request is accepted. |
| **Template** | The message text with placeholders. See [Templates](templates.md). |
| **Aggregation** | An optional platform feature that can group several notifications and send a summary. It is turned off unless administrators enable it. The console has no screen for it. |

## Access terms

| Term | Meaning |
| --- | --- |
| **Keycloak** | The identity provider. It handles sign-in and stores user accounts and roles. |
| **Realm** | A Keycloak space that holds users, roles, and clients. AegisNotify uses one realm, named `aegis`. |
| **Scope** | A permission written into your sign-in token, such as `notification:read`. See [Roles and permissions](roles-and-permissions.md). |
| **JWT / token** | The signed proof of sign-in the console sends with each request. |
| **401 / 403** | See [Troubleshooting](troubleshooting.md#error-401-or-403). |
