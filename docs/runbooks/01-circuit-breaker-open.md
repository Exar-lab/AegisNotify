# Runbook: open circuit breaker

## Background

Each channel has one Resilience4j circuit breaker around its **primary** provider:
`email-provider`, `sms-provider`, `whatsapp-provider`, `push-provider`. All four use the
`default` config in `aegis-notification-service/src/main/resources/application.yml`:

| Setting | Value |
| --- | --- |
| Sliding window | `COUNT_BASED`, size 10 |
| Minimum calls before evaluating | 10 |
| Failure-rate threshold | 50% |
| Slow-call threshold | 40% of calls slower than 2000 ms |
| Wait in `OPEN` | 60 s |
| Calls permitted in `HALF_OPEN` | 3 |
| Automatic `OPEN` to `HALF_OPEN` | enabled |

Retry wraps the breaker: up to 3 attempts, 200 ms initial wait, exponential multiplier 2, and
only for `TransientProviderDeliveryException`. HTTP 429 and any 5xx, plus timeouts, I/O errors
and `WebClientRequestException`, are classified transient. Every other failure is permanent and is
not retried (`RetryClassification`).

When the circuit is open, the call is rejected before any HTTP request is sent and the
notification immediately fails over to the secondary provider (see
[Provider failover](02-provider-failover.md)). Open circuit is therefore a **degradation**, not
an outage, as long as the secondary provider is configured and healthy.

The fifth breaker, `aggregation-agent`, protects the optional LLM summarizer. It uses a 6000 ms
slow-call threshold and is only relevant when `NOTIFICATION_AGGREGATION_ENABLED=true`.

## Symptom

- Admin UI **Providers** page shows "Open circuits" greater than zero, and a card with state
  `OPEN` (or `FORCED OPEN`).
- Admin UI **Dashboard** "Circuit breakers" hotspot shows fewer circuits `closed` than total, in
  a critical tone, and the "Provider circuit breakers" panel lists an `OPEN` provider.
- Notification detail pages show a "Provider attempts" row with code `CIRCUIT_OPEN` and the
  detail "Primary call rejected by the circuit breaker", followed by a `FALLBACK` attempt.
- Fallback activations climb (see runbook 02).

## Detection

| Signal | Where |
| --- | --- |
| Circuit state, failure rate, slow-call rate, last state change, 24h calls | `GET /api/v1/dashboard/circuit-breakers` (port 8082, scope `notification:read`) |
| Same data | Admin UI `/providers` (auto-refresh every 15 s) and `/dashboard` (10 s) |
| Raw Resilience4j state | `GET /actuator/circuitbreakers` (authenticated; exposed in `management.endpoints.web.exposure.include`) |
| Prometheus | `resilience4j_circuitbreaker_state{name="email-provider",state="open"}` and `resilience4j_circuitbreaker_calls_seconds_count{name=...,kind=...}` |
| Health | `GET /actuator/health` includes circuit breakers (`register-health-indicator: true`); `show-details` is not configured for this service, so details are hidden |
| Provider errors | Admin UI `/metrics` "Provider errors", or Prometheus `aegisnotify_provider_error_count_total{provider,error_code}` |
| Logs | `primary_provider_unavailable channel=<CHANNEL> reason=<message>` (WARN) |

There is no committed alert rule. A reasonable alert is
`resilience4j_circuitbreaker_state{state="open"} == 1` held for more than one minute; verify the
exact series names on a live scrape first.

The dashboard reports `UNKNOWN` when the Resilience4j registry has no breaker under the expected
name (`Resilience4jCircuitBreakerAdapter`); treat it as "no data", not as healthy. Failure and
slow-call rates are `null` (shown as an em dash) until 10 calls have been recorded in the window,
so a quiet channel will not show rates even when `CLOSED`.

## Diagnosis

1. **Identify the channel and provider.** On `/providers`, note which card is `OPEN`. The primary
   providers are SendGrid (or Mailtrap if `NOTIFICATION_EMAIL_PROVIDER=mailtrap`), Twilio SMS,
   Twilio WhatsApp (or Vonage if `NOTIFICATION_WHATSAPP_PROVIDER=vonage`), and Firebase Cloud
   Messaging. The Providers screen labels use the static names SendGrid, Twilio SMS, Twilio
   WhatsApp and Firebase Cloud Messaging regardless of the selected vendor.
2. **Find why it opened.** Distinguish failures from slowness:
   - High failure rate: open `/metrics` "Provider errors" and look at `error_code` for that
     provider. Numeric values are HTTP status codes returned by the vendor (401/403 means a
     credential problem, 429 means rate limiting, 5xx means vendor outage). Non-numeric values are
     exception class names (for example `WebClientRequestException` means DNS, TLS or connection
     failure from this host).
   - High slow-call rate with few errors: the vendor is answering but above 2000 ms. Compare
     "Delivery latency" mean and max on `/metrics`, or `aegisnotify_delivery_latency_seconds_*`
     per `provider` tag.
3. **Confirm it is not local.** Check the service can reach the vendor base URL
   (`SENDGRID_BASE_URL`, `TWILIO_BASE_URL`, `FCM_BASE_URL`, `VONAGE_WHATSAPP_BASE_URL`), that
   credentials were not rotated, and that the host has outbound connectivity. The Providers screen
   has no "test" button; use a single new notification after the circuit half-opens.
4. **Check the state timeline.** `lastStateChange` in the circuit-breaker response gives when the
   state last changed. If it flaps between `OPEN` and `HALF_OPEN` roughly every 60 s, the vendor is
   still failing; each half-open probe uses up to 3 calls.
5. **Check the secondary.** If the secondary is unconfigured, every rejected call produces
   `FAILED_CRITICAL`. See step 2 of the [failover runbook](02-provider-failover.md).

## Remediation

There is **no manual control** to open, close, reset, or force a circuit: no endpoint, UI action,
or configuration flag does this. The only automatic recovery path is the configured one.

1. **Fix the cause** (credential, vendor outage, network). The breaker recovers on its own:
   after 60 s in `OPEN` it moves to `HALF_OPEN`, permits 3 calls, and closes if the failure and
   slow-call rates for those probes stay under the thresholds.
2. **Do not restart the service as a first step.** A restart resets the breaker to `CLOSED` and
   clears the in-process metrics shown on `/metrics`, which destroys diagnostic evidence, and the
   breaker will reopen after 10 more failing calls if the cause is unfixed. The configuration
   enables automatic `OPEN` to `HALF_OPEN` transitions, so a restart should not be needed to
   recover a breaker; treat a breaker that stays `OPEN` well beyond 60 s with a healthy vendor as
   a defect to escalate.
3. **Credential problems** (HTTP 401/403): rotate the secret (for example `SENDGRID_API_KEY`),
   then restart the service because provider clients read credentials at startup. The service
   fails fast at startup if a required primary credential is blank.
4. **Threshold tuning** (only if the vendor is healthy and the breaker is too sensitive): the
   values live in `application.yml` under `resilience4j.circuitbreaker`. The notification service
   does not import Config Server, so changing them requires a code or deployment change and a
   restart. There is no runtime override.
5. **Traffic during the outage**: new notifications continue to be accepted (202) and are delivered
   by the secondary provider. Nothing needs to be re-sent for notifications that ended
   `SENT_VIA_FALLBACK`. Notifications that ended `FAILED_CRITICAL` need action; follow
   [FAILED_CRITICAL notifications](05-failed-critical.md).
6. **Verify recovery**: the card returns to `CLOSED`, "Open circuits" returns to 0, and new
   notifications for the channel show `SENT` with a `PRIMARY` attempt.

## Escalation

Escalate when any of these hold:

- A circuit has been `OPEN` or flapping for longer than your channel's delivery objective, and the
  secondary provider is also failing (rising `FAILED_CRITICAL`).
- The provider error code is 401/403 and you do not own the credential.
- All four channels are open at once (suggests a local network, DNS, or proxy problem rather than
  four vendor outages).

Attach: affected channel, `GET /api/v1/dashboard/circuit-breakers` output, the "Provider errors"
table, `lastStateChange`, and one representative notification id with its `/status` response.
The repository defines no on-call roster or paging route; use your team's escalation channel.
