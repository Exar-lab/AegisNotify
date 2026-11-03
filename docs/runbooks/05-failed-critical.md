# Runbook: FAILED_CRITICAL notifications

## Background

`FAILED_CRITICAL` is the terminal status for a notification that could not be delivered by either
provider of its channel. It is produced in these places:

1. **Provider exhaustion** (normal path). `ResilientNotificationProviderAdapter` returns
   `FAILED_CRITICAL` when the primary failed (or its circuit was open) and the secondary also failed
   or is unconfigured. The detail is
   `Primary and secondary providers both failed for channel <CHANNEL>: <secondary error>`.
   `NotificationProcessingTransactions.applyResult` then:
   - saves each attempt to `delivery_attempts`;
   - writes `PROVIDER_A_FAIL` / `PROVIDER_B_FAIL` rows to `notification_logs` for the failed
     primary / fallback attempts;
   - sets the notification to `FAILED_CRITICAL` with the error detail and writes a
     `FAILED_CRITICAL` log row ("Critical failure, sent to DLQ: ...");
   - publishes an audit event.

   The consumer then publishes to `notifications-dlq` (see [runbook 03](03-dlq-messages.md)). The
   log text says "sent to DLQ" because that publish follows; if the publish fails the status is
   still `FAILED_CRITICAL`.
2. **Aggregation fan-out.** When a notification is the leader of an aggregate, its outcome is
   copied to every sibling still in `QUEUED`. All siblings become `FAILED_CRITICAL` with the same
   detail suffixed by the aggregation id, but only the **leader** is sent to the DLQ. The count of
   `FAILED_CRITICAL` notifications can therefore exceed the number of DLQ messages.
3. **Manual**: `SendToDlqService` marks a notification `FAILED_CRITICAL` and publishes to the DLQ,
   but no controller or job exposes it, so it cannot be triggered by an operator today.

Not covered by the status: when processing throws an unexpected exception (for example the template
is missing or inactive), the consumer writes a `FAILED_CRITICAL` row in `notification_logs` and
sends to the DLQ but leaves the notification's `status` as it was (`QUEUED` or `PROCESSING`). See
[runbook 03](03-dlq-messages.md).

**Retry is not possible through the API.** `POST /api/v1/notifications/{id}/retry` only accepts
status `FAILED` and returns **409** otherwise. No code path in the resilient provider adapter
produces `FAILED`, so in practice the retry endpoint does not apply to provider failures.

## Symptom

- Admin UI `/dashboard` "Failed in latest 20" is non-zero and critical.
- Admin UI `/notifications` shows rows with the `FAILED_CRITICAL` badge (use the Status filter).
- The notification detail page shows the notice "Delivery failed critically. Check the timeline for
  dead-letter queue routing details." and a `Failure` for both the `PRIMARY` and `FALLBACK` rows in
  "Provider attempts".
- Dashboard success rate drops (it counts `FAILED_CRITICAL` as terminal and not successful).

## Detection

| Signal | Where |
| --- | --- |
| Count and list | `GET /api/v1/notifications?status=FAILED_CRITICAL&limit=100`; Admin UI `/notifications` Status filter |
| Rate and impact | `GET /api/v1/dashboard/summary` field `successRate` (24h with delta against the previous 24h); Admin UI `/dashboard` |
| Cause per notification | `GET /api/v1/notifications/{id}/status` (audit trail with log details, and delivery attempts with code and error detail); Admin UI `/notifications/:id` "Delivery timeline" and "Provider attempts" |
| Provider error codes | Admin UI `/metrics` "Provider errors"; Prometheus `aegisnotify_provider_error_count_total{provider,error_code}` |
| Circuit state | Admin UI `/providers`; see [runbook 01](01-circuit-breaker-open.md) |
| DLQ evidence | `notifications-dlq` messages (key = notification id, header `reason`); see [runbook 03](03-dlq-messages.md) |
| Audit service | `GET /api/v1/audit/{notificationId}` on `aegis-audit-service` (port 8083), scope `audit:read` |

SQL (compose stack: `docker exec -it aegis-postgres psql -U aegis -d aegisnotify`):

```sql
SELECT id, channel, priority, error_detail, updated_at
FROM notifications
WHERE status = 'FAILED_CRITICAL'
ORDER BY updated_at DESC
LIMIT 50;

SELECT channel, count(*) FROM notifications
WHERE status = 'FAILED_CRITICAL' AND updated_at > now() - interval '1 hour'
GROUP BY channel;
```

No alert rule is shipped. A reasonable alert is a SQL-based check on the count above, because there
is no Prometheus counter of `FAILED_CRITICAL` outcomes (the available counters are provider errors,
fallback transmissions, and Kafka consumer counters).

## Diagnosis

1. **Scope by channel and time.** Group the SQL result by `channel` and `updated_at`. A single
   channel starting at a point in time suggests a provider or credential change. All channels at
   once suggests shared infrastructure (outbound network, DNS).
2. **Read the attempts of a sample.** In "Provider attempts":
   - `PRIMARY` `CIRCUIT_OPEN` plus `FALLBACK` `NOT_CONFIGURED`: circuit open and no secondary
     configured. Fix by configuring the secondary ([runbook 02](02-provider-failover.md)).
   - `PRIMARY` HTTP 401/403 and `FALLBACK` 401/403: both credentials invalid.
   - `PRIMARY` 5xx/429 retried, `FALLBACK` 5xx: vendor outage on both accounts (default secondaries
     point at the same vendor).
   - 4xx other than 401/403/429 on both: payload or sender problem for that recipient or template
     (invalid recipient format for the vendor, unverified sender, wrong content SID).
3. **Check whether it is data-specific.** If only some recipients fail while others succeed, the
   problem is the recipient or content, not the provider.
4. **Check template and parameters.** For notifications whose audit trail shows `Unexpected error:`
   rather than provider failures, verify the template still exists and is active
   (`/templates` screen) and that all variables are supplied.
5. **Confirm no duplicate risk.** Before replaying, check that no `Success` attempt exists for the
   notification. A timeout after the vendor accepted the message can leave `Failure` recorded
   while the end user actually received it.

## Remediation

1. **Stop the bleeding first.** Restore the provider path (credentials, secondary configuration,
   vendor recovery, circuit closed). Replaying before this only creates new failures.
2. **Decide per notification whether delivery is still wanted.** Time-sensitive content (one-time
   codes, expiring links) should not be replayed late. For those, notify the owning team and leave
   the notification as `FAILED_CRITICAL`; there is no "resolved" or "abandoned" status in the data
   model, so record the decision in the incident notes.
3. **Option A, replay the original event (preferred).** Republish the notification's original
   outbox payload to its priority topic so the normal consumer processes it again. The consumer has
   no status guard, so a `FAILED_CRITICAL` notification is reprocessed and its outcome and attempts
   are appended to its history. Steps and caveats are in [runbook 03, section 3](03-dlq-messages.md).
4. **Option B, submit a new notification.** `POST /api/v1/notifications` (through the gateway, scope
   `notification:write`) with the same channel, recipient, template and parameters. This creates a
   new id and a clean audit trail, at the cost of losing the link between the old and new records;
   record both ids. Use this when the original parameters are known or the audit link does not matter.
5. **Option C, force a retry through the API (unsupported).** The retry endpoint needs status
   `FAILED`. A database edit that sets the notification to `FAILED` would let
   `POST /api/v1/notifications/{id}/retry` re-queue it with a new outbox event and a
   "Manual retry requested" log entry. This is a direct data change that bypasses the application,
   is not tested, and the retry endpoint is not routed by the gateway (call the notification service
   on port 8082 with a `notification:write` token). Prefer Option A or B.
6. **Do not mass-replay.** Start with one notification per channel, confirm `SENT` or
   `SENT_VIA_FALLBACK`, then proceed in batches that respect provider rate limits. The gateway rate
   limiter applies only to new submissions (Option B), not to Kafka replays.
7. **Verify**: sampled notifications reach `SENT` / `SENT_VIA_FALLBACK`, "Failed in latest 20"
   returns to zero as new traffic arrives, and the DLQ stops growing.

## Escalation

Escalate when:

- `FAILED_CRITICAL` continues after both providers are confirmed healthy (suggests a code defect or
  data problem).
- The affected messages are regulated or contractual (security alerts, billing notices) and delivery
  must be proven; involve the business owner and provide the audit trail.
- Many notifications failed and replay would exceed provider quotas or sender reputation limits.
- A notification is `FAILED_CRITICAL` with an audit trail that shows no `delivery_attempts` and no
  provider failure (indicates the unexpected-exception path or a defect).

Attach: counts by channel and time window, a sample of notification ids with `/status` output,
"Provider errors" table, circuit-breaker state, and the DLQ `reason` headers. The repository defines
no on-call or paging route.
