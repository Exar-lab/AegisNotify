# Observability: metrics, dashboards, and alerting

Reference for operators (issue #203). It lists every custom metric the platform emits, how to scrape it, and
suggested dashboards and alert rules. For what to do when an alert fires, see the
[runbooks](runbooks/README.md).

- [What is and is not provided](#what-is-and-is-not-provided)
- [Scraping](#scraping)
- [Naming in Prometheus](#naming-in-prometheus)
- [Custom metrics](#custom-metrics)
- [Resilience4j circuit breaker metrics](#resilience4j-circuit-breaker-metrics)
- [Standard metrics](#standard-metrics)
- [Suggested dashboard panels](#suggested-dashboard-panels)
- [Suggested alert rules](#suggested-alert-rules)
- [Gaps](#gaps)

## What is and is not provided

Provided: Micrometer metrics exposed at `/actuator/prometheus` by the gateway and the notification, audit,
and user services.

**Not provided:** the repository contains no Prometheus server, Grafana, alert rules, dashboards, or scrape
configuration, and neither compose file starts them. Everything under *Scraping*, *Suggested dashboard
panels*, and *Suggested alert rules* is a starting point you deploy and tune yourself.

How much was verified:

- **Metric names, types, tags, and Prometheus spellings** in [Custom metrics](#custom-metrics) were taken from
  the source and checked by registering each meter in a Micrometer 1.14.2 `PrometheusMeterRegistry` and
  reading its scrape output. They have not been checked against a running service.
- **Scrape config and alert rules** were not run against a real Prometheus.

## Scraping

`/actuator/prometheus` is public (no token) on each service that exposes it.

| Service | Port | Metrics endpoint |
|---|---|---|
| `aegis-api-gateway` | 8080 | `/actuator/prometheus` |
| `aegis-notification-service` | 8082 | `/actuator/prometheus` (also `/actuator/circuitbreakers`) |
| `aegis-audit-service` | 8083 | `/actuator/prometheus` |
| `aegis-user-service` | 8084 | `/actuator/prometheus` |
| `aegis-eureka-server`, `aegis-config-server` | 8761, 8888 | none (only `health` and `info` are exposed) |

Sample `prometheus.yml` for services running on the host:

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: aegis-notification-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["localhost:8082"]

  - job_name: aegis-api-gateway
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["localhost:8080"]

  - job_name: aegis-audit-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["localhost:8083"]

  - job_name: aegis-user-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["localhost:8084"]
```

If Prometheus runs in the same Docker network as `docker-compose.full.yml`, use the container names instead
(for example `aegis-notification-service:8082`). Do not expose `/actuator/prometheus` on a public network.

**Keep demo metrics off.** `AEGIS_DEMO_METRICS_ENABLED=true` makes `DemoMetricsSeeder` write synthetic
values into `aegisnotify.requests.total`, `delivery.latency.seconds`, `provider.error.count`, and
`fallback.transmissions`. It defaults to `false`; leave it that way anywhere you rely on these metrics.

## Naming in Prometheus

Meter names in the code use dots. Prometheus exposes them with underscores, and counters gain a `_total`
suffix:

| In code | In Prometheus |
|---|---|
| `aegisnotify.requests.total` (counter) | `aegisnotify_requests_total` |
| `aegisnotify.provider.error.count` (counter) | `aegisnotify_provider_error_count_total` |
| `aegisnotify.delivery.latency.seconds` (timer) | `aegisnotify_delivery_latency_seconds_count`, `_sum` |

## Custom metrics

All of these are emitted by `aegis-notification-service`. Counters are cumulative since the service started
and reset on restart, so always query them with `rate()` or `increase()`.

| Prometheus name | Type | Tags | Meaning |
|---|---|---|---|
| `aegisnotify_requests_total` | counter | `channel`, `priority` | Notifications accepted by `POST /api/v1/notifications` |
| `aegisnotify_delivery_latency_seconds` | summary (`_count`, `_sum`) | `provider` | Duration of each outbound provider call, whatever the outcome |
| `aegisnotify_provider_error_count_total` | counter | `provider`, `error_code` | Failed provider calls |
| `aegisnotify_fallback_transmissions_total` | counter | none | Sends that went to the secondary provider after the primary failed |
| `aegisnotify_outbox_publish_error_count_total` | counter | `reason` | Failures publishing an outbox event to Kafka |
| `aegisnotify_dlq_publish_error_count_total` | counter | `reason` | Failures publishing a message to the dead-letter queue |
| `aegisnotify_aggregation_flush_success_count_total` | counter | none | Aggregation windows flushed successfully |
| `aegisnotify_aggregation_flush_error_count_total` | counter | `reason` | Aggregation flush failures |
| `notification_kafka_consumer_success_total` | counter | `topic` | Messages processed by the Kafka consumer |
| `notification_kafka_consumer_failure_total` | counter | `topic` | Consumer processing failures |
| `notification_kafka_consumer_retry_total` | counter | `topic` | Consumer retries |
| `notification_kafka_consumer_dlt_total` | counter | `topic` | Messages sent to a dead-letter topic |

Tag values:

- `channel`: `EMAIL`, `SMS`, `WHATSAPP`, `PUSH`. `priority`: `HIGH`, `MEDIUM`, `LOW`.
- `provider`: `SendGrid`, `Mailtrap` (email), `Twilio` (SMS and WhatsApp), `Vonage` (WhatsApp), `FCM` (push).
  The set emitted depends on which adapters your deployment enables.
- `error_code`: the HTTP status code of the provider response when there is one (for example `503`),
  otherwise the simple name of the exception class (for example `TimeoutException`). Expect it to be
  high-cardinality in the second case; aggregate it away in dashboards.
- Publish error `reason`: `interrupted`, `execution`, `timeout`.
- Aggregation flush error `reason`: `claim_failed`, `render_failed`, `summarizer_unavailable`,
  `flush_failed`, `force_poison_row_failed`.

Notes:

- The latency metric is a **summary**, not a histogram: it has `_count` and `_sum` but no buckets or
  quantiles, so `histogram_quantile()` does not work on it. Use the average,
  `rate(..._sum) / rate(..._count)`. No percentile or histogram configuration exists in the repository.
- `aegisnotify_requests_total` counts accepted requests, not deliveries. Delivery outcomes are visible
  through the status API and the audit trail, and indirectly through the provider metrics above.
- There is no counter for terminal states (`SENT`, `FAILED_CRITICAL`, ...). The admin console's metrics
  screen reads aggregate values from `GET /api/v1/metrics/summary`.

## Resilience4j circuit breaker metrics

The notification service has one circuit breaker instance per channel plus one for the aggregation agent:
`email-provider`, `sms-provider`, `whatsapp-provider`, `push-provider`, and `aggregation-agent`.

Settings (from `application.yml`): count-based window of 10 calls, minimum 10 calls, opens at a 50% failure
rate or a 40% slow-call rate, slow means over 2000 ms (6000 ms for `aggregation-agent`), stays open for 60 s,
then half-open with 3 trial calls. It moves to half-open on its own.

| Prometheus name | Type | Tags | Meaning |
|---|---|---|---|
| `resilience4j_circuitbreaker_state` | gauge | `name`, `state` | `1` for the breaker's current `state`, `0` for the others |
| `resilience4j_circuitbreaker_calls_seconds_count` | timer | `name`, `kind` | Calls by outcome (`successful`, `failed`, `ignored`) |

The repository's tests assert these two names. Resilience4j also publishes `resilience4j_circuitbreaker_failure_rate`,
`resilience4j_circuitbreaker_slow_call_rate`, and `resilience4j_circuitbreaker_not_permitted_calls_total`;
those are standard Resilience4j meters that this repository does not assert, so confirm them on a live scrape.
The current state is also available as JSON at `/actuator/circuitbreakers` and in `/actuator/health`.

## Standard metrics

Spring Boot, Micrometer, and the JVM also publish their usual metrics on every service. The ones most useful
here:

- `http_server_requests_seconds_*` (by `uri`, `status`, `method`): API latency and error rates.
- `jvm_memory_*`, `jvm_gc_*`, `process_cpu_usage`: resource use.
- `hikaricp_connections_*`: PostgreSQL pool saturation, on services that use it.

The gateway also publishes its own HTTP metrics; which gateway-specific meters appear was not checked.

## Suggested dashboard panels

One row per concern. PromQL below assumes the scrape jobs above.

| Panel | Query | Why |
|---|---|---|
| Request rate by channel | `sum by (channel) (rate(aegisnotify_requests_total[5m]))` | Traffic shape |
| Request rate by priority | `sum by (priority) (rate(aegisnotify_requests_total[5m]))` | Priority mix |
| Provider error rate | `sum by (provider) (rate(aegisnotify_provider_error_count_total[5m]))` | Which provider is failing |
| Provider errors by code | `sum by (provider, error_code) (rate(aegisnotify_provider_error_count_total[15m]))` | Cause (HTTP status); keep the time range short |
| Average provider latency | `sum by (provider) (rate(aegisnotify_delivery_latency_seconds_sum[5m])) / sum by (provider) (rate(aegisnotify_delivery_latency_seconds_count[5m]))` | Slow providers |
| Fallback share | `rate(aegisnotify_fallback_transmissions_total[5m]) / sum(rate(aegisnotify_requests_total[5m]))` | Primary health |
| Circuit breaker state | `resilience4j_circuitbreaker_state == 1` (legend: `{{name}} {{state}}`) | Which breakers are open |
| Consumer outcomes | `sum by (topic) (rate(notification_kafka_consumer_success_total[5m]))`, and the `failure`, `retry`, and `dlt` equivalents | Pipeline health per priority topic |
| Publish and DLQ errors | `sum by (reason) (rate(aegisnotify_outbox_publish_error_count_total[5m]))`, `... aegisnotify_dlq_publish_error_count_total ...` | Broker problems |
| API errors | `sum by (status) (rate(http_server_requests_seconds_count{status=~"4..\|5.."}[5m]))` | Client and server errors |

## Suggested alert rules

These are starting points. Thresholds are guesses, not tuned to your traffic. Validate them (for example
with `promtool check rules`) before you deploy them.

```yaml
groups:
  - name: aegisnotify
    rules:
      - alert: AegisCircuitBreakerOpen
        expr: resilience4j_circuitbreaker_state{state="open"} == 1
        for: 1m
        labels: { severity: critical }
        annotations:
          summary: "Circuit breaker {{ $labels.name }} is open"
          runbook: "docs/runbooks/01-circuit-breaker-open.md"

      - alert: AegisFallbackRateHigh
        expr: |
          rate(aegisnotify_fallback_transmissions_total[10m])
            / clamp_min(sum(rate(aegisnotify_requests_total[10m])), 0.001) > 0.1
        for: 10m
        labels: { severity: warning }
        annotations:
          summary: "More than 10% of sends are using the fallback provider"
          runbook: "docs/runbooks/02-provider-failover.md"

      - alert: AegisProviderErrors
        expr: sum by (provider) (rate(aegisnotify_provider_error_count_total[5m])) > 0.5
        for: 5m
        labels: { severity: warning }
        annotations:
          summary: "Provider {{ $labels.provider }} is returning errors"
          runbook: "docs/runbooks/02-provider-failover.md"

      - alert: AegisDeliveryLatencyHigh
        expr: |
          sum by (provider) (rate(aegisnotify_delivery_latency_seconds_sum[5m]))
            / sum by (provider) (rate(aegisnotify_delivery_latency_seconds_count[5m])) > 2
        for: 10m
        labels: { severity: warning }
        annotations:
          summary: "Average latency for {{ $labels.provider }} is above 2 s"
          runbook: "docs/runbooks/01-circuit-breaker-open.md"

      - alert: AegisOutboxPublishFailing
        expr: sum(increase(aegisnotify_outbox_publish_error_count_total[10m])) > 0
        for: 10m
        labels: { severity: critical }
        annotations:
          summary: "Outbox events are failing to publish to Kafka"
          runbook: "docs/runbooks/04-outbox-backlog.md"

      - alert: AegisMessagesDeadLettered
        expr: |
          sum(increase(notification_kafka_consumer_dlt_total[10m])) > 0
            or sum(increase(aegisnotify_dlq_publish_error_count_total[10m])) > 0
        labels: { severity: critical }
        annotations:
          summary: "Messages reached a dead-letter topic or the DLQ could not be written"
          runbook: "docs/runbooks/03-dlq-messages.md"

      - alert: AegisTargetDown
        expr: up{job=~"aegis-.*"} == 0
        for: 2m
        labels: { severity: critical }
        annotations:
          summary: "{{ $labels.job }} is not being scraped"
          runbook: "docs/runbooks/06-dependency-outages.md"
```

Reading the thresholds:

- `AegisFallbackRateHigh` is a ratio of fallback sends to all accepted requests. Both counters move with
  traffic, so it is meaningful only when traffic is steady. `clamp_min` avoids dividing by zero.
- `AegisDeliveryLatencyHigh` uses 2 s because that is the circuit breaker's slow-call threshold; fire before
  the breaker does.
- `AegisMessagesDeadLettered` has no `for:` on purpose, since any dead-lettered message needs a person.

## Gaps

Things you may expect and will not find:

- **Outbox lag or depth.** No metric reports how many outbox events are waiting or how old the oldest is.
  `AegisOutboxPublishFailing` catches publish *errors*, not a worker that has stopped or is slow. Until a
  gauge exists, detect a backlog with the SQL in [runbook 04](runbooks/04-outbox-backlog.md) or by
  counting notifications stuck in `PENDING`.
- **Terminal-state counters.** No metric counts notifications by final status.
- **Latency percentiles.** The latency metric is a summary without quantiles or buckets.
- **Audit and user service business metrics.** Those services expose only the standard metrics.
