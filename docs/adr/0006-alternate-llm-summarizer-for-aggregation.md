# ADR-0006: Config-selectable LLM summarizer (Anthropic or Groq) for aggregation

- **Status:** Accepted
- **Date:** 2026-09-02 (summarizer port and Anthropic adapter, `feb2568`); 2026-11-02 (Groq adapter,
  PR #193, `88cca45`)
- **Evidence:** `bf0d701`, `feb2568`, `88cca45`, `AggregationConfig`, `SummarizerCallTemplate`,
  `AggregationSummarizerPort`, `application.yml`

## Context

To reduce notification volume, the service can buffer notifications for the same recipient and
channel and merge a group of two or more into one message (issue #86, `bf0d701`). The merge is done
by an LLM. The first implementation (`feb2568`) used the Anthropic Messages API. Summarization
must never become a new source of message loss.

## Decision

Keep summarization behind an outbound port, `AggregationSummarizerPort`, and select the vendor by
configuration (`88cca45`):

- `notification.aggregation.summarizer.vendor` is `anthropic` (default) or `groq`. Groq uses its
  Chat Completions API, default model `llama-3.3-70b-versatile`.
- `AggregationConfig` returns the adapter for the selected vendor. If aggregation is off or the
  selected vendor has no API key, it returns `UnavailableSummarizerAdapter` instead of failing
  startup. This mirrors the EMAIL and WHATSAPP vendor selectors in `ProviderConfig`.
- `SummarizerCallTemplate` was extracted so both adapters share the `aggregation-agent` circuit
  breaker, failure handling, JSON payload parsing, and output escaping. Only the vendor HTTP call
  and its JSON shapes differ.
- Any failure (timeout, open breaker, error response, unusable output) falls the whole group back
  to individual delivery (`feb2568`), so aggregation cannot lose messages.
- Startup rejects a summarizer timeout at or beyond the aggregation window or the claim lease, to
  avoid duplicate delivery from a re-claimed buffer row.
- Output is HTML-escaped for EMAIL before it is capped to `max-output-chars`.

`88cca45` also fixes a defect found while testing Groq live: a notification already resolved out of
the buffer was re-held on every outbox poll indefinitely. Resolved events now carry
`skipAggregation: true`, and `PublishOutboxEventService` publishes them unconditionally.

## Alternatives considered

- **Anthropic only.** This was the state between `feb2568` and `88cca45`; the second vendor was
  added as a configurable alternative. The repository does not record why Groq was chosen. The
  commit message describes it only as "alternate" (reasons such as cost or latency would be
  inferred from implementation, and are not stated).
- **Duplicate the breaker and parsing logic per vendor.** Not chosen; `88cca45` extracted
  `SummarizerCallTemplate` "without duplicating it".
- **One breaker per vendor.** Not chosen; the template documents a single `aggregation-agent`
  circuit because only one vendor is wired at a time.

## Consequences

### Positive

- The vendor can be switched with `AGGREGATION_LLM_VENDOR` and an API key, with no code change.
- Resilience and output-trust behavior is identical across vendors.
- Aggregation is off by default (`NOTIFICATION_AGGREGATION_ENABLED=false`) and degrades safely.

### Negative / trade-offs

- Message content is sent to an external LLM vendor when aggregation is enabled.
- Only one vendor is active at a time; there is no automatic failover between Anthropic and Groq.
- Shared parsing assumes both vendors honor the `{"subject","body"}` JSON instruction; non-conforming
  output falls back to individual delivery.
- Some Anthropic-named settings (`api-key`, `model`, `anthropic-version`) sit beside the Groq block
  in the same `summarizer` section, which is easy to misconfigure.

## Related

- [ADR-0001](0001-transactional-outbox.md) (relay and hold guard), [ADR-0002](0002-resilience4j-circuit-breakers-per-provider.md)
- [Aggregation and summarizer guide](../aggregation.md) (configuration, failure behavior, schema).
- Issue #86; PR #193.
