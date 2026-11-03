# API integration guide

How to call AegisNotify from another system (issue #202). Everything goes through the API gateway
(`http://localhost:8080` locally); you never call the backend services directly.

- [Quick start](#quick-start)
- [Authentication](#authentication)
- [Create a template](#create-a-template)
- [Submit a notification](#submit-a-notification)
- [Poll the status](#poll-the-status)
- [Status lifecycle](#status-lifecycle)
- [Cancel and retry](#cancel-and-retry)
- [List notifications](#list-notifications)
- [Errors](#errors)
- [Idempotency](#idempotency)
- [Rate limits](#rate-limits)
- [Postman](#postman)
- [API changelog](#api-changelog)

The machine-readable contract is the OpenAPI spec each service exposes under the `local` Spring profile
(see [`postman/README.md`](postman/README.md)). OpenAPI and Swagger UI are disabled outside that profile.

## Quick start

```bash
# 1. token (local development client, see Authentication)
ACCESS_TOKEN=$(curl -s -X POST http://localhost:8088/realms/aegis/protocol/openid-connect/token \
  -d "grant_type=password" -d "client_id=aegis-dev-cli" \
  -d "username=aegis-dev" -d "password=dev123" \
  -d "scope=notification:write notification:read" | jq -r .access_token)

# 2. submit (the template must already exist, see "Create a template")
curl -i -X POST http://localhost:8080/api/v1/notifications \
  -H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json" \
  -d '{"channel":"EMAIL","recipient":"user@example.com","templateName":"welcome-email","parameters":{"name":"Ada"}}'

# 3. poll, using the id from the 202 response
curl -s http://localhost:8080/api/v1/notifications/<id>/status -H "Authorization: Bearer $ACCESS_TOKEN" | jq
```

## Authentication

Every endpoint except the actuator health, info, and Prometheus endpoints needs a Keycloak-issued JWT in
`Authorization: Bearer <token>`. Authorization is by OAuth2 **scope**:

| Scope | What it allows in this guide |
|---|---|
| `notification:write` | Submit, cancel, and retry notifications; create and edit templates |
| `notification:read` | Poll status, list notifications, list templates |

Scopes are not hierarchical. Full tables are in [security/scopes.md](security/scopes.md) and the realm
setup is in `docs/security/keycloak.md` (see PR #219).

**Local development.** The imported realm provides the public client `aegis-dev-cli` and user `aegis-dev`
(password `dev123`); the request in the quick start uses them. They exist only for local use.

**Your own integration.** The realm ships no client for machine-to-machine callers. Create a confidential
client with the *Client credentials* flow in Keycloak, give it the `notification:write` and
`notification:read` client scopes, and request tokens with `grant_type=client_credentials`. This is a
configuration you add; it is not part of the repository's realm file.

Tokens expire. Request a new one when you receive a `401`.

## Create a template

A notification refers to a template by name, and the name must belong to an **active** template, otherwise
the submit returns `404`. The service has no seed data, so create one per channel you use
(`POST /api/v1/templates`, scope `notification:write`):

```bash
curl -s -X POST http://localhost:8080/api/v1/templates \
  -H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json" \
  -d '{
    "name": "welcome-email",
    "channel": "EMAIL",
    "subject": "Welcome, {{name}}!",
    "body": "<p>Hi {{name}}, welcome to AegisNotify.</p>",
    "variables": ["name"]
  }'
```

| Field | Rules |
|---|---|
| `name` | required, unique, at most 120 characters; a duplicate returns `409` |
| `channel` | required: `EMAIL`, `SMS`, `WHATSAPP`, or `PUSH` |
| `subject` | optional |
| `body` | required |
| `variables` | optional list of the variable names the template uses |

Placeholders use Mustache syntax (`{{name}}`). The submit endpoint looks the template up **by name only**; it
does not check that the template's channel matches the notification's channel, so keep names
channel-specific (`welcome-email`, `otp-sms`).

## Submit a notification

`POST /api/v1/notifications`, scope `notification:write`.

| Field | Required | Rules |
|---|---|---|
| `channel` | yes | `EMAIL`, `SMS`, `WHATSAPP`, `PUSH` |
| `recipient` | yes | non-blank, at most 320 characters, format depends on the channel (below) |
| `templateName` | yes | non-blank, at most 120 characters, an active template |
| `parameters` | no | JSON object of template variables; defaults to `{}` |
| `priority` | no | `HIGH`, `MEDIUM`, `LOW`; defaults to `MEDIUM` |

Recipient format by channel:

| Channel | Format | Example |
|---|---|---|
| `EMAIL` | `local@domain.tld`, TLD of 2 to 4 characters | `user@example.com` |
| `SMS`, `WHATSAPP` | E.164: `+`, then 2 to 15 digits, first digit not 0 | `+34600000000` |
| `PUSH` | any non-blank string (the device token your push provider expects) | `fcm-device-token` |

The response is `202 Accepted` with a `Location` header pointing at the status URL and a body of
`{"id": "<uuid>", "status": "PENDING"}`. `202` means the request was stored for asynchronous delivery,
not that anything was sent.

### Examples per channel

Each example assumes a matching active template exists (see
[Create a template](#create-a-template)).

**Email**

```bash
curl -i -X POST http://localhost:8080/api/v1/notifications \
  -H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json" \
  -d '{
    "channel": "EMAIL",
    "recipient": "user@example.com",
    "templateName": "welcome-email",
    "parameters": {"name": "Ada"},
    "priority": "MEDIUM"
  }'
```

**SMS**

```bash
curl -i -X POST http://localhost:8080/api/v1/notifications \
  -H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json" \
  -d '{
    "channel": "SMS",
    "recipient": "+34600000000",
    "templateName": "otp-sms",
    "parameters": {"code": "482913"},
    "priority": "HIGH"
  }'
```

**WhatsApp**

```bash
curl -i -X POST http://localhost:8080/api/v1/notifications \
  -H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json" \
  -d '{
    "channel": "WHATSAPP",
    "recipient": "+34600000000",
    "templateName": "order-shipped-whatsapp",
    "parameters": {"orderId": "A-1042"}
  }'
```

**Push**

```bash
curl -i -X POST http://localhost:8080/api/v1/notifications \
  -H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json" \
  -d '{
    "channel": "PUSH",
    "recipient": "<device-token>",
    "templateName": "new-message-push",
    "parameters": {"sender": "Ada"},
    "priority": "LOW"
  }'
```

Delivery to real providers needs their credentials configured on the notification service (SendGrid,
Twilio, Firebase Cloud Messaging). With placeholders the request is accepted but delivery fails; see the
[README configuration](../README.md#configuration).

### What is checked at submit time

Checked: the JSON shape, the field limits above, the recipient format for the channel, and that an active
template with that name exists. **Not checked:** that `parameters` covers every placeholder in the template.
Rendering happens later, during processing. To verify a template and its parameters before you send,
use the render preview endpoint if your deployment has it (`POST /api/v1/templates/render`, added in
PR #218).

## Poll the status

`GET /api/v1/notifications/{id}/status`, scope `notification:read`. Returns `200` with:

| Field | Meaning |
|---|---|
| `id`, `channel`, `recipient`, `templateName`, `priority` | What was submitted |
| `status` | Current state, see below |
| `createdAt`, `updatedAt` | Timestamps (ISO 8601) |
| `auditTrail` | Timestamped log of state changes |
| `providerAttempts` | Each delivery attempt against a provider |
| `correlationId` | Identifier for tracing across services |

An unknown id returns `404`. Poll with a modest interval and stop at a terminal status; there is no webhook
or callback.

## Status lifecycle

```text
PENDING -> QUEUED -> PROCESSING -> SENT
                               \-> SENT_VIA_FALLBACK
                               \-> FAILED
                               \-> FAILED_CRITICAL
PENDING / QUEUED -> CANCELLED
```

| Status | Meaning | Terminal |
|---|---|---|
| `PENDING` | Accepted and stored, waiting to be published | no |
| `QUEUED` | Published to the broker | no |
| `PROCESSING` | A consumer is rendering and sending | no |
| `SENT` | Delivered by the primary provider | yes |
| `SENT_VIA_FALLBACK` | Delivered by the secondary provider after the primary failed | yes |
| `FAILED` | Delivery failed; can be retried manually | yes, until retried |
| `FAILED_CRITICAL` | Both providers failed; the message went to the dead-letter queue and needs manual intervention | yes |
| `CANCELLED` | Cancelled before processing | yes |

Operational handling of failures is in the [runbooks](runbooks/README.md).

## Cancel and retry

| Action | Request | Allowed when | Otherwise |
|---|---|---|---|
| Cancel | `PATCH /api/v1/notifications/{id}/cancel` | status is `PENDING` or `QUEUED` | `409` |
| Retry | `POST /api/v1/notifications/{id}/retry` | status is `FAILED` | `409` |

Both need `notification:write`, return `200` with `{"id","status"}`, and return `404` for an unknown id.
`FAILED_CRITICAL` cannot be retried through the API.

## List notifications

`GET /api/v1/notifications`, scope `notification:read`. There are two modes, chosen by the parameters:

- **Recent list** (none of `page`, `search`, `recipient`, `priority`, `from`, `to`): optional `channel` and
  `status` filters and `limit` (default 20, between 1 and 100; outside that range returns `400`).
- **Paged search** (any of the others present): `search`, `recipient`, `channel`, `priority`, `status`,
  `from` and `to` (ISO 8601 instants), `page` (zero-based, default 0), and `size` (default 20).

## Errors

Errors from the notification service share one JSON shape:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Template not found: welcome-email",
  "timestamp": "2026-11-03T14:30:00Z"
}
```

| Status | When |
|---|---|
| `400` | Validation failed, malformed JSON body, invalid recipient for the channel, a bad parameter value, or `limit` out of range |
| `401` | Missing, malformed, or expired token (from the gateway or the service) |
| `403` | Token valid but lacks the required scope |
| `404` | Unknown notification id, or template name that is unknown or inactive |
| `409` | Duplicate template name; cancelling a notification that is not `PENDING`/`QUEUED`; retrying one that is not `FAILED` |
| `422` | A template cannot be rendered with the given parameters (render preview endpoint) |
| `429` | Rate limit exceeded on submit, see [Rate limits](#rate-limits) |
| `500` | Unexpected error; the message is generic and the detail is in the server log |

Two details:

- A `403` from the **gateway** names the missing scope: `{"error","message","requiredScope","path",
  "timestamp"}`. A `403` that comes from a backend service has Spring Security's opaque default body.
- `401` and `403` bodies are not the shape above; branch on the HTTP status, not on the body.

## Idempotency

**Submitting is not idempotent.** There is no idempotency key. Each successful `POST /api/v1/notifications`
creates a new notification with a new id, so retrying a request whose response you never received can send
the message twice.

To retry safely after a timeout or a `5xx`:

1. If you did not get a `202`, you cannot know whether the notification was stored. Look for it with the
   paged search (`recipient`, `from`, `to`) before submitting again.
2. Treat `429` as safe to retry: the request was rejected before reaching the service.
3. Delivery inside the service is at-least-once, so a recipient can in rare cases receive a duplicate even
   when you submitted once.

## Rate limits

Only `POST /api/v1/notifications` is rate limited, at the gateway, with a Redis token bucket. Defaults:

| Setting | Default | Environment variable |
|---|---|---|
| Replenish rate | 100 tokens per second | `RATE_LIMIT_REPLENISH_RATE` |
| Burst capacity | 1200 tokens | `RATE_LIMIT_BURST_CAPACITY` |
| Tokens per request | 60 | `RATE_LIMIT_REQUESTED_TOKENS` |

With those defaults one caller can send a burst of about 20 requests (1200 / 60), then sustain about 1.7
requests per second (100 / 60). Operators may have changed them.

- The bucket is keyed by the token's `sub` claim, or by its `jti` when `sub` is absent. The gateway code
  notes that this realm's dev tokens were observed to carry no `sub`; for those, **each new token gets a
  fresh bucket**. Do not rely on that: with a standard token the bucket is per user or client.
- Over the limit, the gateway answers `429`. The standard Spring Cloud Gateway `X-RateLimit-*` headers
  indicate the remaining tokens and the configured rate.
- If Redis is unreachable the limiter lets traffic through (fails open).
- A request with no resolvable key is rejected with `403`.

For bulk sends, pace your requests and back off on `429`.

## Postman

[`postman/AegisNotify.postman_collection.json`](postman/AegisNotify.postman_collection.json) covers every
endpoint of the notification, user, and audit services. The **Quick start** folder holds ready-to-send
examples for each channel. Setup and regeneration are in [`postman/README.md`](postman/README.md).

## API changelog

Breaking and notable changes to the public API surface. Release-level history is in
[`CHANGELOG.md`](../CHANGELOG.md).

### Unreleased

- **Added** gateway routes for `PATCH /api/v1/notifications/{id}/cancel` and
  `POST /api/v1/notifications/{id}/retry`. The endpoints already existed on the notification service, but
  were not reachable through the gateway (PR #215).
- **Pending** `POST /api/v1/templates/render` (scope `notification:write`) returns the rendered subject and
  body for a template and parameters (PR #218, not yet merged).
