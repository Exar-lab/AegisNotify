# Notification aggregation and AI summarizer

This document describes the optional aggregation feature of `aegis-notification-service`: how
notifications are held, merged by a large language model (LLM), and delivered, how to configure it,
and what happens when something fails. Every statement is derived from the code and
`application.yml` of the notification service; the decision record is
[ADR-0006](adr/0006-alternate-llm-summarizer-for-aggregation.md).

Aggregation is **disabled by default** (`NOTIFICATION_AGGREGATION_ENABLED=false`). When it is
disabled, no notification is held and no summarizer call is made.

## Contents

- [How it works](#how-it-works)
- [Prerequisites](#prerequisites)
- [Which notifications are held](#which-notifications-are-held)
- [Configuration reference](#configuration-reference)
- [Provider selection](#provider-selection)
- [Model-assigned priority](#model-assigned-priority)
- [API keys and secrets](#api-keys-and-secrets)
- [Limits](#limits)
- [Failure and fallback behavior](#failure-and-fallback-behavior)
- [Database objects](#database-objects)
- [Observability](#observability)
- [Admin UI and API](#admin-ui-and-api)
- [Live Groq smoke check](#live-groq-smoke-check)
- [Known gaps](#known-gaps)

## How it works

Aggregation sits between the transactional outbox and the Kafka broker. A notification is created
and stored exactly as usual (`POST /api/v1/notifications`, see [ARCHITECTURE.md](ARCHITECTURE.md)).
The change happens when the outbox relay picks up its event.

```mermaid
flowchart TD
    A[Outbox relay polls UNPROCESSED events<br/>PublishOutboxEventService] --> B{Event aggregatable?<br/>AggregationPolicy}
    B -- "no (disabled, HIGH priority, excluded,<br/>or skipAggregation=true)" --> P[Publish to Kafka immediately<br/>publishOne]
    B -- yes --> H[holdForAggregation<br/>insert aggregation_buffer row BUFFERED,<br/>outbox row PROCESSED,<br/>notification stays PENDING]
    H --> W[Window expires<br/>expires_at = hold time + window]
    W --> F[AggregationWindowScheduler polls<br/>FlushAggregationWindowsService]
    F --> C[Claim expired rows<br/>BUFFERED to CLAIMED]
    C --> G{Rows sharing a group key<br/>channel + recipient [+ template]}
    G -- "group of 1" --> I[Individual delivery]
    G -- "group of 2 or more" --> R[Render each member's template]
    R --> S[Summarizer call<br/>no DB transaction open]
    S -- success --> L[flushAggregate<br/>one outbox event for the leader,<br/>siblings QUEUED, rows DONE]
    S -- "any failure" --> I
    R -- "render failure" --> I
    I --> E[flushIndividually<br/>fresh outbox event with skipAggregation=true,<br/>row DONE]
    L --> P2[Normal relay publishes event<br/>skipAggregation=true]
    E --> P2
    P2 --> K[Kafka consumer calls the provider once<br/>for the leader]
    K --> O[Leader outcome copied to<br/>QUEUED siblings]
```

Step by step:

1. **Hold.** `PublishOutboxEventService` asks `AggregationPolicy` whether each pending outbox event
   is aggregatable. If so, `PublishOutboxEventTransactions.holdForAggregation` inserts an
   `aggregation_buffer` row with `expires_at = now + window`, marks the outbox event `PROCESSED`
   without publishing to the broker, and writes a `notification_logs` entry `Held for aggregation`.
   The notification stays `PENDING`; no new notification status exists for this state.
2. **Flush poll.** `AggregationWindowScheduler` runs every `poll-interval` and calls
   `FlushAggregationWindowsService.flushExpiredWindows()`. It selects rows that are either
   `BUFFERED` with `expires_at <= now`, or `CLAIMED` with `claimed_at` older than the claim lease
   (stale claims).
3. **Claim.** Each row is claimed in its own short transaction with a conditional update, so only
   one claimant wins a concurrent race. The claim increments `attempts`.
4. **Group.** Claimable rows are grouped by key: channel and recipient, plus template name when
   `require-same-template` is `true`. A group with more rows than `max-group-size` is split,
   oldest buffer row first, into consecutive chunks of at most `max-group-size` rows; every chunk
   is flushed in the same poll, so no row is deferred or lost. A trailing chunk of one row is
   released individually like any other group of one.
5. **Single row.** A group with one row is released for individual delivery with no summarizer call.
6. **Summarize.** For a group of two or more, each member's template body is rendered at flush time
   (not at hold time). The summarizer is then called with no database transaction open. The prompt
   asks for a JSON object `{"subject", "body"}` and gives a soft length hint of 480 characters.
   With `model-priority-enabled` it also asks for a `priority` (see
   [Model-assigned priority](#model-assigned-priority)).
7. **Leader and siblings.** On success, `AggregationFlushTransactions.flushAggregate` runs in one
   transaction:
   - the leader is the oldest member by notification creation time; it gets the new `aggregation_id`,
     the summarized text in `aggregate_body` and the summarizer's subject in `aggregate_subject`,
     and receives the only outbox event;
   - every other member (sibling) gets the same `aggregation_id`, is moved to `QUEUED`, and gets no
     outbox event and no provider call;
   - every member's buffer row becomes `DONE`;
   - an audit event is published per member, including the leader, with the aggregation id in its
     details.
8. **Delivery.** The leader is delivered through the normal pipeline. When the consumer renders the
   leader, a non-null `aggregate_body` replaces the template rendering. The delivered subject is the
   stored `aggregate_subject`; when the summarizer returned no subject (or a blank one), it falls
   back to the leader's template subject (the template is still loaded for that).
9. **Outcome fan-out.** After the leader's outcome is applied, the same outcome (and an audit entry)
   is copied to each sibling that is still `QUEUED`. Siblings in any other status, for example
   `CANCELLED`, are left untouched.

## Prerequisites

Aggregation depends on the outbox relay. The hold decision and the release of flushed events both
run through it, and the relay is disabled by default. To use aggregation, enable all of:

| Setting | Why |
|---|---|
| `NOTIFICATION_KAFKA_RELAY_ENABLED=true` | The relay is where events are held and where flushed events are published. With the relay off, nothing is ever held or released. |
| `NOTIFICATION_AGGREGATION_ENABLED=true` | Turns on the hold guard and the flush scheduler. |
| An API key for the selected vendor | Without one, summarization is unavailable and every group falls back to individual delivery (see [Provider selection](#provider-selection)). |

`NOTIFICATION_KAFKA_CONSUMER_ENABLED` must also be on for messages to be consumed and delivered, as
for any notification.

## Which notifications are held

`AggregationPolicy.isAggregatable` applies these checks in order; the first match sends the
notification down the unchanged immediate-publish path:

1. Aggregation is disabled.
2. Priority is `HIGH`. High-priority notifications are never held.
3. The template name is blank or missing (treated as excluded, fail-safe).
4. The template name is listed in `excluded-templates` (trimmed, case-insensitive comparison).
5. The channel is listed in `excluded-channels`.

In addition, `PublishOutboxEventService` never holds an event whose payload carries
`skipAggregation: true` (see [Re-hold loop fix](#re-hold-loop-fix)).

## Configuration reference

All keys live under `notification.aggregation` in
`aegis-notification-service/src/main/resources/application.yml`, each bound to an environment
variable with the default shown. Durations use ISO-8601 format (for example `PT5M`).

### Core settings

| Property | Environment variable | Default | Purpose |
|---|---|---|---|
| `enabled` | `NOTIFICATION_AGGREGATION_ENABLED` | `false` | Master switch for holding and flushing. |
| `window` | `NOTIFICATION_AGGREGATION_WINDOW` | `PT5M` | How long a notification is held before its row becomes eligible to flush. |
| `poll-interval` | `NOTIFICATION_AGGREGATION_POLL_INTERVAL` | `PT10S` | Fixed delay between flush polls (`@Scheduled(fixedDelay)`). |
| `require-same-template` | `NOTIFICATION_AGGREGATION_REQUIRE_SAME_TEMPLATE` | `true` | When `true`, a group needs the same channel, recipient and template; when `false`, channel and recipient only. |
| `max-group-size` | `NOTIFICATION_AGGREGATION_MAX_GROUP_SIZE` | `20` | Maximum notifications summarized into one aggregate. Larger claimable groups are split across chunks in the same poll. See [Limits](#limits). |
| `claim-lease` | `NOTIFICATION_AGGREGATION_CLAIM_LEASE` | `PT2M` | How long a row may stay `CLAIMED` before a later poll may re-claim it. |
| `max-attempts` | `NOTIFICATION_AGGREGATION_MAX_ATTEMPTS` | `3` | Claim attempts tolerated before a row that keeps failing individual release is released loose as its own notification. See [Failure and fallback behavior](#failure-and-fallback-behavior). |
| `model-priority-enabled` | `NOTIFICATION_AGGREGATION_MODEL_PRIORITY_ENABLED` | `false` | Lets the summarizer assign the priority of the aggregated message. Off: behavior is unchanged. See [Model-assigned priority](#model-assigned-priority). |
| `excluded-templates` | `NOTIFICATION_AGGREGATION_EXCLUDED_TEMPLATES` | empty | Comma-separated template names that always bypass aggregation. |
| `excluded-channels` | `NOTIFICATION_AGGREGATION_EXCLUDED_CHANNELS` | empty | Comma-separated channels (`EMAIL`, `SMS`, `WHATSAPP`, `PUSH`) that always bypass aggregation. |

`window`, `poll-interval` and `claim-lease` must be positive durations, and `max-group-size` and
`max-attempts` must be greater than zero; otherwise startup fails with an `IllegalStateException`.

### Summarizer settings

Keys under `notification.aggregation.summarizer`. The first block is shared by both vendors unless
noted.

| Property | Environment variable | Default | Purpose |
|---|---|---|---|
| `vendor` | `AGGREGATION_LLM_VENDOR` | `anthropic` | Selects the adapter: `anthropic` or `groq` (case-insensitive). Any other value behaves like `anthropic`. |
| `base-url` | `AGGREGATION_LLM_BASE_URL` | `https://api.anthropic.com` | Anthropic API base URL. Anthropic only. |
| `api-key` | `AGGREGATION_LLM_API_KEY` | empty | Anthropic API key, sent as the `x-api-key` header. Anthropic only. |
| `model` | `AGGREGATION_LLM_MODEL` | `claude-sonnet-4-5` | Anthropic model identifier. Anthropic only. |
| `anthropic-version` | `AGGREGATION_LLM_API_VERSION` | `2023-06-01` | Value of the `anthropic-version` header. Anthropic only. |
| `max-tokens` | `AGGREGATION_LLM_MAX_TOKENS` | `512` | Output token limit. Sent as `max_tokens` (Anthropic) or `max_completion_tokens` (Groq). Both vendors. |
| `timeout` | `AGGREGATION_LLM_TIMEOUT` | `PT10S` | Response timeout of the HTTP call. Both vendors. |
| `max-output-chars` | `AGGREGATION_LLM_MAX_OUTPUT_CHARS` | `2000` | Hard cap on the summarized body that gets stored. Both vendors. |

Groq-specific keys under `notification.aggregation.summarizer.groq`:

| Property | Environment variable | Default | Purpose |
|---|---|---|---|
| `base-url` | `AGGREGATION_LLM_GROQ_BASE_URL` | `https://api.groq.com/openai` | Groq OpenAI-compatible API base URL. |
| `api-key` | `AGGREGATION_LLM_GROQ_API_KEY` | empty | Groq API key, sent as a Bearer token. |
| `model` | `AGGREGATION_LLM_GROQ_MODEL` | `llama-3.1-8b-instant` | Groq model identifier. |

`max-tokens`, `timeout` and `max-output-chars` must be positive; a blank `base-url`, `model` or
`anthropic-version` falls back to the default.

### Circuit breaker

The summarizer call runs inside the Resilience4j circuit breaker `aggregation-agent`
(`resilience4j.circuitbreaker.instances.aggregation-agent`). It inherits the `default` config
(count-based window of 10, minimum 10 calls, 50% failure rate, 40% slow-call rate, 60 s in the open
state, 3 permitted calls in half-open) and overrides one value:
`slow-call-duration-threshold: 6000ms`, because the shared 2000 ms default would classify normal
LLM latency as slow. The same breaker name is used by both vendors, since only one is wired at a
time. See [runbook 01](runbooks/01-circuit-breaker-open.md).

## Provider selection

`AggregationConfig.aggregationSummarizerPort` returns exactly one adapter at startup:

| Condition | Adapter |
|---|---|
| Aggregation disabled, **or** the selected vendor's API key is blank | `UnavailableSummarizerAdapter` (always throws; every group falls back to individual delivery). Startup does not fail. |
| `vendor=anthropic` (default) and key set | `AnthropicMessagesSummarizerAdapter`, `POST {base-url}/v1/messages`. |
| `vendor=groq` and key set | `GroqChatCompletionsSummarizerAdapter`, `POST {groq.base-url}/v1/chat/completions` with `response_format: json_object`. |

Only the selected vendor's key is checked. There is no automatic failover between vendors: if the
selected vendor fails, the group is delivered individually rather than retried against the other
vendor.

To use Groq:

```bash
export NOTIFICATION_KAFKA_RELAY_ENABLED=true
export NOTIFICATION_AGGREGATION_ENABLED=true
export AGGREGATION_LLM_VENDOR=groq
export AGGREGATION_LLM_GROQ_API_KEY=<your Groq key>
# optional: AGGREGATION_LLM_GROQ_MODEL, AGGREGATION_LLM_GROQ_BASE_URL
```

### Enable aggregation with Groq

Steps for the Docker Compose stack (`docker-compose.full.yml`), which passes the variables below
from the host environment or `.env` to `aegis-notification-service`. Compose already enables the
outbox relay and the Kafka consumer, so no extra prerequisite setting is needed there.

1. Create a Groq API key in your Groq account.
2. Copy `.env.example` to `.env` (gitignored) if you have not already, and set:

   ```bash
   NOTIFICATION_AGGREGATION_ENABLED=true
   AGGREGATION_LLM_VENDOR=groq
   AGGREGATION_LLM_GROQ_API_KEY=<your Groq key>
   # optional: NOTIFICATION_AGGREGATION_WINDOW (default PT5M),
   # AGGREGATION_LLM_GROQ_MODEL, AGGREGATION_LLM_GROQ_BASE_URL
   ```

   Use the `AGGREGATION_LLM_GROQ_*` key, not `AGGREGATION_LLM_API_KEY`, which is the Anthropic key.
3. Recreate the service so it picks up the new environment:

   ```bash
   docker compose -f docker-compose.full.yml up -d --build aegis-notification-service
   ```
4. Confirm the resolved values:

   ```bash
   docker compose -f docker-compose.full.yml config
   ```

   Check that the notification service shows `NOTIFICATION_AGGREGATION_ENABLED: "true"` and
   `AGGREGATION_LLM_VENDOR: groq`. This output prints the key in clear text; do not share it.
5. Submit two or more non-`HIGH` notifications to the same recipient, channel and template within
   the window. After the window plus up to one poll interval, one aggregated message is delivered.
   If no key reaches the service, groups are delivered individually (metric reason
   `summarizer_unavailable`).

For a host run without Compose, export the same variables plus
`NOTIFICATION_KAFKA_RELAY_ENABLED=true` as shown above. End-to-end behavior against the live Groq
API was not verified for this guide.

Both vendors share one system prompt and one response-handling path (`SummarizerCallTemplate`): the
completion text is parsed as `{"subject": ..., "body": ...}` (plus `"priority"` when
[requested](#model-assigned-priority)), the body is HTML-escaped for `EMAIL`
and then capped to `max-output-chars`. Escaping happens before the cap so the stored body can never
exceed the cap.

## Model-assigned priority

By default the released aggregate keeps the leader's priority. With
`notification.aggregation.model-priority-enabled=true` (env
`NOTIFICATION_AGGREGATION_MODEL_PRIORITY_ENABLED`, default `false`) the summarizer also judges how
urgent the group is, and that priority is applied when the group is released.

**Contract.** The prompt asks for `{"subject": string, "body": string, "priority": string}` where
`priority` is `HIGH`, `MEDIUM` or `LOW`. Both vendors use the same prompt and parsing. The value is
trimmed, matched case-insensitively against the `Priority` enum, and any other value (or none) is
treated as no suggestion. With the flag off, the prompt is the original one and a `priority` in the
reply is ignored.

**Applying it.** In `flushAggregate` the leader is saved with the applied priority and its outbox
event carries it, so the relay publishes to the matching topic (`high-priority-topic`,
`medium-priority-topic` or `low-priority-topic`). The audit event for the leader reports the
applied priority too. The leader's notification log entry records both values, for example
`Aggregated as leader of a 3-notification group (aggregation <id>); priority: suggested HIGH,
applied HIGH`, or `priority: no valid suggestion, applied MEDIUM`.

**Safeguards.**

- The model can only keep or raise the priority. The applied value is never lower than the highest
  priority among the group's members; a reply of `LOW` for a group containing a `MEDIUM` member is
  applied as `MEDIUM`. Without a valid suggestion, the leader's priority is kept (also never below
  that floor).
- Notification content is untrusted. In this mode each rendered body is wrapped in
  `<message index="N">...</message>` and the system prompt tells the model to treat those tags as
  data, to ignore instructions inside them, and to choose the priority only from how urgent the
  events are. A body that contains its own `<message>` or `</message>` tag has it replaced by
  square brackets, so it cannot close the data block.
- Only rendered bodies reach the model (no recipient, ids or raw parameters), as before.

**Limits.** Prompt wording reduces, but cannot eliminate, prompt injection: a crafted notification
can still push the model to raise the priority, because raising is allowed. The priority can
never fall, so the worst effect of such a bump is routing to a more urgent topic. `HIGH`
notifications are still never held (they bypass aggregation), so a group's members are `MEDIUM` or
`LOW`; the model can promote the aggregate to `HIGH`, and the aggregate is not re-held
(`skipAggregation`). Siblings keep their own stored priority; only the leader, which is the one
delivered, changes. The flag is global, not per template or channel.

## API keys and secrets

- Keys are read from the environment variables listed above; `application.yml` ships an empty
  default for both, so no credential is committed.
- A blank key is not validated at binding time and does not block startup. Aggregation defaults to
  disabled, so a missing key must not prevent the service from starting. Instead the config
  degrades to `UnavailableSummarizerAdapter`.
- The key is attached once, when the adapter is built: as the `x-api-key` header (Anthropic) or as a
  Bearer token (Groq).
- Note the naming: the unqualified `AGGREGATION_LLM_API_KEY`, `..._BASE_URL` and `..._MODEL`
  variables configure **Anthropic**. Groq uses the `AGGREGATION_LLM_GROQ_*` variants. Setting
  `AGGREGATION_LLM_VENDOR=groq` while only `AGGREGATION_LLM_API_KEY` is set yields the unavailable
  adapter.
- How secrets are injected in a given deployment (Config Server, container environment, secret
  manager) is outside this service's code and is not covered here.
- When aggregation is enabled and a key is set, the rendered bodies of the grouped notifications
  (channel, template name and message text) are sent to the selected external vendor. Consider this
  before enabling aggregation for notifications containing personal or sensitive data. See the
  trade-offs in ADR-0006.

## Limits

| Limit | Value | Enforced by |
|---|---|---|
| Hold time | `window` (default 5 minutes) | `expires_at` set at hold time; a poll considers only rows with `expires_at <= now`. |
| Flush latency | up to `poll-interval` (default 10 s) after the window ends | Scheduler fixed delay. |
| Summarizer timeout | `timeout` (default 10 s) | Must be strictly less than both `window` and `claim-lease`; checked at startup (see below). |
| Summary length | `max-output-chars` (default 2000) | Hard cap on the stored body. The prompt separately hints at 480 characters (a constant, not configurable). |
| Subject length | 255 characters (constant) | The summarizer subject is stripped of control characters (line breaks become spaces), trimmed and capped; a blank result is stored as null and the template subject is used. |
| Output tokens | `max-tokens` (default 512) | Passed to the vendor. |
| Group size | `max-group-size` (default 20) | `FlushAggregationWindowsService` splits a larger group, oldest buffer row first, into chunks of at most this size, all flushed in the same poll. Each chunk is summarized separately, so a group of N rows costs `ceil(N / max-group-size)` summarizer calls; a chunk of one row is released individually. |
| Retry of failed releases | `max-attempts` (default 3) | After this many failed claims the row is released loose (see below). |

**Startup invariants.** When aggregation is enabled and the selected vendor has a key,
`AggregationConfig` throws `IllegalStateException` and the service refuses to start if
`summarizer.timeout >= window` or `summarizer.timeout >= claim-lease`. The claim-lease check exists
because the summarizer call runs while the row is `CLAIMED` with no transaction open; a timeout at
or beyond the lease would let a later poll re-claim the row mid-call and produce two outbox writes
for one notification (duplicate delivery). These checks are skipped when the unavailable adapter is
selected.

## Failure and fallback behavior

The guiding rule is that aggregation must not lose messages: any failure on the aggregation path
releases the affected notifications to normal individual delivery.

| Failure | Result | Metric reason |
|---|---|---|
| Aggregation disabled, or no key for the selected vendor | `UnavailableSummarizerAdapter` throws on every call; each group of two or more is released individually. | `summarizer_unavailable` |
| Summarizer timeout, HTTP error, network error | Collapsed to `SummarizerUnavailableException`; the whole group is released individually. | `summarizer_unavailable` |
| `aggregation-agent` breaker open | No HTTP request is made; the whole group is released individually. | `summarizer_unavailable` |
| Unusable output (blank content, unparseable JSON, empty body, no choices/content) | Treated as unavailable; whole group released individually. | `summarizer_unavailable` |
| A member's notification or active template is missing, or rendering fails | That member alone is released individually; the rest continue. If fewer than two members remain renderable, all remaining members are released individually. | `render_failed` |
| Persisting the aggregate fails (`flushAggregate` throws, transaction rolled back, for example leader not found) | Every member is released individually. | `flush_failed` |
| Claim fails (database error) | The row is skipped for this poll and retried on a later one. | `claim_failed` |
| Another poll already claimed the row | Skipped silently. | none |
| Individual release itself fails | The row stays `CLAIMED` and is retried after `claim-lease` expires. | `flush_failed` |
| Individual release keeps failing and `attempts > max-attempts` | Poison-row guard: the row is released loose, that is delivered as its own notification (see the note below). | `flush_failed`, then `force_poison_row_failed` if the loose release itself fails (the row then stays `CLAIMED` and is retried after `claim-lease`) |

**Individual release** (`flushIndividually`) writes a fresh outbox event with the notification's
original payload plus `skipAggregation: true`, adds a `PENDING` log entry (`Released from
aggregation buffer for individual delivery`), and marks the row `DONE`. If the notification no
longer exists, a `FAILED` log entry is written and the row still becomes `DONE`, so a row is never
held forever.

**Poison-row guard.** It exists to stop a row from looping CLAIMED, lease expiry, re-claim
indefinitely without ever delivering. Once `attempts > max-attempts` and the individual release still
fails, `AggregationFlushTransactions.releaseLoose` runs in its own transaction: it writes a fresh
outbox event with the notification's original payload plus `skipAggregation: true` and marks the
buffer row `DONE`, so the notification is delivered on its own and is never held again. A best-effort
`PENDING` log entry (`Released from aggregation buffer for individual delivery after N failed
attempts (<reason>)`) is then written in a separate transaction; a failure of that log write does not
undo the release. If the notification no longer exists, a `FAILED` log entry is written and the row
becomes `DONE`. If the loose release itself fails, nothing is marked done: the row stays `CLAIMED`,
the metric `force_poison_row_failed` is incremented, and a later poll tries again after
`claim-lease`. A row is therefore never closed without either an outbox event or an explicit
`FAILED` log entry. Because `attempts` is incremented on each claim and the check is
`attempts > max-attempts`, the guard fires on the claim after the `max-attempts`-th; the default of 3
applies on the fourth failing claim.

**Leader failures.** If the leader's delivery fails or goes to `FAILED_CRITICAL`, the same outcome is
copied to its `QUEUED` siblings. Only the leader is published to the DLQ, so the count of
`FAILED_CRITICAL` notifications can exceed the number of DLQ messages (see
[runbook 05](runbooks/05-failed-critical.md)).

### Re-hold loop fix

PR #193 (commit `88cca45`) fixed a defect found while testing the Groq summarizer. A notification
released from the buffer got a new outbox event whose channel, priority and template were identical
to the original. The relay's aggregation guard only inspects those fields, so it treated the new
event as a brand-new notification and held it again on the next poll, repeating forever.

The fix: every outbox event written by the flush path (individual release and aggregate leader)
carries `skipAggregation: true` in its payload, and `PublishOutboxEventService.isAggregatable`
returns `false` for such events, so they are published unconditionally. Notifications created
through `POST /api/v1/notifications` never set the flag, so they still go through the hold decision.
`RetryFailedNotificationService` does not set it either, so a manual retry re-enters aggregation
like a new delivery attempt.

## Database objects

Flyway migration `V3__add_notification_aggregation.sql` (additive; the new columns are nullable):

| Object | Definition |
|---|---|
| `notifications.aggregation_id` | `UUID`, shared by the leader and siblings of one aggregate; null when never aggregated. Partial index `idx_notifications_aggregation_id` where not null. |
| `notifications.aggregate_body` | `TEXT`, the summarized body; set on the leader only. |
| `notifications.aggregate_subject` | `VARCHAR(255)`, the summarizer subject; set on the leader only, null when the summarizer returned none. Added by `V7__add_aggregate_subject.sql` (additive, nullable). |
| `aggregation_buffer` | One row per held notification: `id`, `notification_id` (FK to `notifications`), `channel`, `recipient`, `template_name`, `priority`, `status` (`BUFFERED`, `CLAIMED`, `DONE`), `expires_at`, `claimed_at`, `attempts`, `created_at`. |
| Indexes on `aggregation_buffer` | `idx_aggregation_buffer_claimable (status, expires_at, claimed_at)` for the flush scan, and `idx_aggregation_buffer_notification_id`. |

Useful query to inspect the buffer (from runbook 04):

```sql
SELECT status, count(*), min(expires_at) FROM aggregation_buffer GROUP BY status;
```

## Observability

| Signal | Detail |
|---|---|
| Counter `aegisnotify.aggregation.flush.success.count` | Incremented per row resolved (individually) and per aggregate flush. |
| Counter `aegisnotify.aggregation.flush.error.count` | Tagged `reason`: `claim_failed`, `render_failed`, `summarizer_unavailable`, `flush_failed`, `force_poison_row_failed`. |
| Log events | `aggregation_flush_tick_completed`, `aggregation_flush_tick_failed`, `aggregation_buffer_claim_failed`, `aggregation_member_render_failed`, `aggregation_summarizer_unavailable`, `aggregation_summarizer_breaker_open`, `aggregation_summarizer_call_failed`, `aggregation_group_flush_failed`, `aggregation_buffer_flush_failed`, `aggregation_buffer_force_poison_row_failed`, `aggregation_buffer_released_loose`, `aggregation_buffer_loose_release_trace_failed`. |
| Circuit breaker `aggregation-agent` | Exposed by Resilience4j health and `/actuator/circuitbreakers`. It is not one of the four providers in the admin Providers view (that view lists only the email, SMS, WhatsApp and push breakers). |

## Admin UI and API

Aggregation state is exposed additively in the status API and shown in the admin notification
detail screen.

- `GET /api/v1/notifications/{id}/status` keeps all its existing fields and adds an `aggregation`
  object (always present; older clients simply ignore it):

  | Field | Meaning |
  |---|---|
  | `role` | `NONE` (never aggregated, including a row still held in the buffer), `LEADER`, `SIBLING` or `RELEASED_INDIVIDUALLY`. |
  | `aggregationId` | Shared id of the aggregate (`LEADER` and `SIBLING` only, else null). |
  | `groupSize` | Notifications folded into the aggregate, leader included (`LEADER` and `SIBLING` only, else null). |
  | `leaderId` | Id of the leader (the notification itself for `LEADER`; `SIBLING` only otherwise, else null). |

  The role is derived on read, nothing extra is persisted: `LEADER` is a notification with an
  `aggregation_id` and an `aggregate_body`, `SIBLING` has an `aggregation_id` without a body, and
  `RELEASED_INDIVIDUALLY` has no `aggregation_id` but a `Released from aggregation buffer for
  individual delivery` log entry.
- The admin notification detail page shows an Aggregation row in the details panel for `LEADER`,
  `SIBLING` (with a link to the leader) and `RELEASED_INDIVIDUALLY`; nothing is shown for `NONE`.
- The `auditTrail` is still built from `notification_logs`, so the timeline also shows:
  - while held: status `PENDING`, log `Held for aggregation`;
  - leader after a flush: log `Aggregated as leader of a N-notification group (aggregation <id>)`;
  - sibling after a flush: status `QUEUED`, log `Folded into aggregate led by <leader id>
    (aggregation <id>)`;
  - individual release: log `Released from aggregation buffer for individual delivery`.
- Once the leader is delivered, siblings receive the same final status and a log entry suffixed with
  the aggregation id. Siblings never make a provider call of their own, so they have no
  provider attempts of their own (derived from the code path; not checked in the UI).
- A sibling is `QUEUED` between the flush and the leader's outcome and remains cancelable in that
  window; the outcome fan-out skips siblings that are no longer `QUEUED` (for example `CANCELLED`).
- The audit trail in the audit service has one event per folded notification, with the aggregation
  id in the free-text details.

## Live Groq smoke check

`GroqSummarizerLiveSmokeTest` calls the real Groq API through `GroqChatCompletionsSummarizerAdapter`
with a small batch of three realistic order-update messages and asserts that the returned body and
subject are non-blank and within limits (body at most `max-output-chars`, subject at most 255
characters). It is opt-in: it is skipped unless `AGGREGATION_LLM_GROQ_API_KEY` is set, so normal and
CI runs never touch the network. The key is read from the environment only and is never logged.

```bash
export AGGREGATION_LLM_GROQ_API_KEY=<your Groq key>
# optional: AGGREGATION_LLM_GROQ_MODEL (default llama-3.1-8b-instant), AGGREGATION_LLM_GROQ_BASE_URL
./mvnw -pl aegis-notification-service test -Dtest=GroqSummarizerLiveSmokeTest
```

A second test in the same class requests the priority (the flag-on prompt) and asserts that the
reply carries a valid `HIGH`, `MEDIUM` or `LOW`.

A passing run confirms that the configured model accepts `response_format: json_object` and returns
the `{"subject","body"}` shape (and `priority` when requested) the adapter parses. Without the key the test is reported as skipped.

## Known gaps

- No automatic vendor failover.
- Not verified here: end-to-end behavior against the live Anthropic or Groq APIs (the opt-in
  [live Groq smoke check](#live-groq-smoke-check) exists but has not been run in this repository's
  CI), and production sizing of `window`, `timeout` and thresholds.
- A group larger than `max-group-size` is summarized in several chunks, so the recipient receives
  one aggregated message per chunk (and one individual message for a trailing single row), not one
  message for the whole group.

The earlier gaps (`max-group-size` not enforced, summarizer subject not stored, poison rows dropped,
no aggregation state in the status API or admin UI) were closed by issue #225.
