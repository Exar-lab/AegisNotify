# AegisNotify

[![CI status](https://github.com/Exar-lab/AegisNotify/actions/workflows/ci.yml/badge.svg)](https://github.com/Exar-lab/AegisNotify/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot 3.4.1](https://img.shields.io/badge/Spring%20Boot-3.4.1-6DB33F?logo=springboot&logoColor=white)
![Maven Wrapper](https://img.shields.io/badge/build-Maven%20Wrapper-C71A36?logo=apachemaven&logoColor=white)
![Hexagonal Architecture](https://img.shields.io/badge/architecture-Hexagonal-4B5563)

AegisNotify is a Java 21 notification orchestration platform for accepting template-based
notifications, persisting their lifecycle, routing work by priority through Kafka, delivering
messages through channel-specific providers, and recording a searchable audit trail.

The repository is organized as six Spring Boot services and applies Hexagonal (Ports and
Adapters) Architecture to the notification, audit, and user domains. Email, SMS, WhatsApp, and
push provider adapters are present. The complete asynchronous delivery path is still under active
development; see [Current implementation status](#current-implementation-status) and
[Project scope](#project-scope) before trying to run the full platform.

> [!IMPORTANT]
> The repository contains the core services and most processing behavior, but it is not yet a
> one-command runnable platform. The notification outbox still lacks its Kafka publishing adapter
> and runtime trigger; see [Current implementation status](#current-implementation-status).

## Navigate

- [At a glance](#at-a-glance)
- [Project scope](#project-scope)
- [Installation](#installation)
- [Services and HTTP API](#services)
- [Security](#security)
- [Configuration](#configuration)
- [Testing and quality checks](#testing-and-quality-checks)
- [Current implementation status](#current-implementation-status)

For system diagrams, the notification lifecycle, Kafka interfaces, persistence, and resilience
details, see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). To contribute, see
[`CONTRIBUTING.md`](CONTRIBUTING.md).

## At a glance

| Capability | Current state |
| --- | --- |
| Accept and query notifications over HTTP | Implemented |
| PostgreSQL persistence and Flyway schema | Implemented |
| Email, SMS, WhatsApp, and push provider adapters | Implemented |
| Priority-based Kafka consumer and dead-letter topics | Implemented, disabled by default |
| MongoDB-backed audit ingestion and search | Implemented |
| Local Keycloak realm with JWT scopes | Implemented (`docker-compose.yml`) |
| User management against Keycloak (read, create, update, disable, reset password) | Implemented; not yet proxied through the API Gateway |
| JWT validation at the gateway and resource services | Implemented, including per-route scope enforcement at the gateway |
| Eureka discovery and Git-backed Config Server | Implemented |
| Prometheus actuator endpoints and Kafka consumer counters | Implemented |
| Transactional outbox relay to Kafka | Application use case implemented; runtime trigger and outbound broker adapter are not yet implemented |
| Provider circuit breakers and secondary-account failover | Implemented and unit-tested; production and end-to-end validation remain |
| RabbitMQ transport | Not implemented |
| One-command full-stack local environment | Not provided — `docker-compose.yml` starts Keycloak only; PostgreSQL, Kafka, and MongoDB are started separately (see [Installation](#installation)) |

## Project scope

AegisNotify is a **portfolio / learning project**, not a production product. Its purpose is to
demonstrate a realistic, non-trivial notification platform built with production-grade patterns —
Hexagonal Architecture, the Transactional Outbox pattern, per-channel circuit breakers with
provider failover, OAuth2/JWT scope-based authorization enforced at two layers (gateway and
downstream service), and event-driven processing over Kafka — rather than to ship a
ready-to-deploy product.

### In scope

- Accepting, persisting, and tracking the lifecycle of Email/SMS/WhatsApp/Push notifications.
- Priority-based asynchronous processing via Kafka, with dead-letter handling.
- Channel provider integration (SendGrid, Twilio, Firebase Cloud Messaging) behind a resilient,
  failover-capable abstraction.
- A searchable, encrypted audit trail of every notification's lifecycle, in a separate service and
  datastore.
- Centralized service discovery (Eureka), externalized configuration (Config Server), and a single
  authenticated entry point (API Gateway) with per-route OAuth2 scope enforcement.
- Basic user administration against Keycloak (list, create, update, disable, password reset) as a
  supporting service for the identity provider driving the rest of the platform.
- Architectural boundary enforcement via ArchUnit, and a real (not mocked-out) local Keycloak realm
  for exercising the security model end to end.

### Out of scope (for now, and possibly permanently)

- **A one-command full-stack environment.** `docker-compose.yml` is intentionally Keycloak-only
  (see the comment at the top of that file); PostgreSQL, Kafka/Zookeeper, and MongoDB are run
  separately, as documented in [Installation](#installation). A `docker-compose.full.yml` bundling
  everything, including the app containers, is a possible future addition, not a current goal.
- **Multi-tenancy.** The platform assumes a single tenant/realm.
- **A notification composition UI or template editor.** Templates are managed directly in
  PostgreSQL; there is no admin UI. Static mockups exist under [`design/`](design/) but no UI code
  has been written yet.
- **RabbitMQ, or any broker other than Kafka.** The domain model and outbound port are broker-
  agnostic in principle, but only a Kafka adapter exists.
- **Horizontal scaling, multi-region, or high-availability operational concerns.** This is a
  single-node local-dev-oriented project; production hardening (connection pooling tuning, secrets
  management, TLS termination, autoscaling, chaos testing) is out of scope.
- **User self-service (signup, login UI, password self-reset).** `aegis-user-service` is an
  administrative API for operators, not an end-user identity product.
- **Deleting users.** By design, `aegis-user-service` only disables users; there is no delete
  endpoint and none is planned (see the Javadoc on `UserController`).

If you're evaluating this repository, treat it as a demonstration of architecture and patterns
first, and a runnable demo second — see [Current implementation status](#current-implementation-status)
for the concrete gaps between "the pattern is implemented" and "the whole flow runs end to end."

## Installation

This section takes you from a fresh clone to every service running locally, including the
external infrastructure that isn't bundled by `docker-compose.yml`.

### Prerequisites

- JDK 21
- Docker and Docker Compose, for Keycloak, PostgreSQL, Kafka, MongoDB, and (optionally)
  Testcontainers-based integration tests
- `jq`, to extract the access token from the Keycloak password-grant response below
- Valid SendGrid, Twilio, and Firebase Cloud Messaging credentials to start the notification
  service with real provider delivery (a placeholder value lets the service start, but sends will
  fail against the real vendor APIs)

Maven does not need to be installed separately because the repository includes `mvnw` and
`mvnw.cmd`.

### 1. Clone and build

```bash
git clone https://github.com/Exar-lab/AegisNotify.git
cd AegisNotify
./mvnw clean package -DskipTests
```

Windows PowerShell or Command Prompt uses `.\mvnw.cmd` instead of `./mvnw` throughout this guide.

### 2. Start Keycloak (identity provider)

```bash
docker compose up -d keycloak
```

This starts Keycloak on `http://localhost:8088` and auto-imports the `aegis` realm from
[`docker/keycloak/aegis-realm.json`](docker/keycloak/aegis-realm.json): the five OAuth2 scopes
(`notification:write`, `notification:read`, `audit:read`, `user:read`, `user:admin`), a public
`aegis-dev-cli` client with direct-access-grants enabled, and a test user (`aegis-dev` / `dev123`,
local development only).

Confirm it's up:

```bash
curl -s http://localhost:8088/realms/aegis/.well-known/openid-configuration
```

### 3. Start PostgreSQL (notification service datastore)

No compose service is provided yet for this — run it directly with Docker, matching the
notification service's defaults (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD` in
[Configuration](#configuration)):

```bash
docker run -d --name aegis-postgres \
  -e POSTGRES_DB=aegisnotify \
  -e POSTGRES_USER=aegis \
  -e POSTGRES_PASSWORD=aegis \
  -p 5432:5432 \
  postgres:15
```

Flyway creates the schema automatically the first time `aegis-notification-service` starts — no
manual migration step is needed.

### 4. Start MongoDB (audit service datastore)

Matches the audit service's default `MONGODB_URI`:

```bash
docker run -d --name aegis-mongo \
  -p 27017:27017 \
  mongo:7
```

### 5. Start Kafka

Using Kafka's built-in KRaft mode (no separate Zookeeper container needed), matching the default
`KAFKA_BOOTSTRAP_SERVERS=localhost:9092`:

```bash
docker run -d --name aegis-kafka \
  -p 9092:9092 \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e CLUSTER_ID=aegis-local-kraft-cluster \
  confluentinc/cp-kafka:7.6.0
```

Use the `local` Spring profile for the notification and audit services against a single local
broker (replication factor and min in-sync replicas of 1 instead of the production defaults of 3
and 2) — this is already wired into the run commands in step 8.

### 6. Provision a template

The notification service has no template management endpoint or seed migration yet — insert at
least one active template directly before submitting a notification:

```bash
docker exec -it aegis-postgres psql -U aegis -d aegisnotify -c "
INSERT INTO templates (id, name, channel, subject, body, variables, active, created_at, updated_at)
VALUES (
  gen_random_uuid(), 'welcome-email', 'EMAIL', 'Welcome, {{name}}!',
  '<p>Hi {{name}}, welcome to AegisNotify.</p>', '[\"name\"]', true, now(), now()
);"
```

Adjust column names if they've drifted from
[`db/migration/V1__create_schema.sql`](db/migration/V1__create_schema.sql) — check that migration
for the authoritative `templates` schema before running this against a newer version of the
project than this guide was written against.

### 7. Set provider and encryption environment variables

```bash
export SENDGRID_API_KEY=your-sendgrid-key
export SENDGRID_FROM_ADDRESS=noreply@yourdomain.com
export TWILIO_ACCOUNT_SID=your-twilio-sid
export TWILIO_AUTH_TOKEN=your-twilio-token
export TWILIO_SMS_FROM_NUMBER=+15551234567
export TWILIO_WHATSAPP_FROM_NUMBER=+15551234567
export FCM_PROJECT_ID=your-firebase-project
export FCM_ACCESS_TOKEN=your-fcm-access-token
export NOTIFICATION_KAFKA_CONSUMER_ENABLED=true
```

The `local` Spring profile provides a development-only `AUDIT_ENCRYPTION_KEY` fallback, so it does
not need to be set for local runs. See [Configuration](#configuration) for the complete variable
reference, including secondary-provider variables for failover.

### 8. Start the services

In separate terminals, in this order:

```bash
./mvnw -pl aegis-eureka-server spring-boot:run
./mvnw -pl aegis-config-server spring-boot:run
./mvnw -pl aegis-notification-service spring-boot:run -Dspring-boot.run.profiles=local
./mvnw -pl aegis-audit-service spring-boot:run -Dspring-boot.run.profiles=local
./mvnw -pl aegis-user-service spring-boot:run
./mvnw -pl aegis-api-gateway spring-boot:run
```

- Eureka (`http://localhost:8761`) has no external dependency and is the simplest to verify first.
- Config Server requires its own Git repository and Basic Auth environment variables
  (`CONFIG_SERVER_USER`, `CONFIG_SERVER_PASSWORD`, `CONFIG_REPO_URI`) if you intend to use
  centralized configuration; it's optional for a local run using each service's own
  `application.yml`.
- `aegis-user-service` additionally requires `KEYCLOAK_ADMIN_CLIENT_SECRET` for the confidential
  `aegis-user-service` client that calls the Keycloak Admin API — retrieve it from the Keycloak
  admin console (`http://localhost:8088`, `admin` / `admin` by default) under the `aegis` realm's
  client credentials, or add it to `docker/keycloak/aegis-realm.json` and re-import the realm.
- Even with every dependency above running, the complete submit-to-delivery flow (HTTP submit →
  outbox → Kafka → provider delivery) is still blocked by the missing outbox broker adapter and
  relay trigger — see [Current implementation status](#current-implementation-status).

### 9. Get a token and call the API

```bash
ACCESS_TOKEN=$(curl -s -X POST http://localhost:8088/realms/aegis/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=aegis-dev-cli" \
  -d "username=aegis-dev" \
  -d "password=dev123" \
  -d "scope=notification:write notification:read audit:read user:read user:admin" \
  | jq -r .access_token)

curl -i -X POST http://localhost:8080/api/v1/notifications \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "EMAIL",
    "recipient": "user@example.com",
    "templateName": "welcome-email",
    "parameters": { "name": "Ada" },
    "priority": "HIGH"
  }'
```

`aegis-dev-cli` and `aegis-dev` exist only for local development — see
[`docker/keycloak/aegis-realm.json`](docker/keycloak/aegis-realm.json) for the exact definitions.
If scopes drift after editing that file, re-import with
`docker compose down -v && docker compose up -d keycloak`. Full scope contract:
[`docs/security/scopes.md`](docs/security/scopes.md).

### Tearing down

```bash
docker compose down -v
docker rm -f aegis-postgres aegis-mongo aegis-kafka
```

## Services

| Module | Default port | Responsibility |
| --- | ---: | --- |
| [`aegis-api-gateway`](aegis-api-gateway/) | 8080 | Reactive entry point, JWT validation, per-route scope enforcement, CORS, and Eureka load-balanced routing |
| [`aegis-eureka-server`](aegis-eureka-server/) | 8761 | Netflix Eureka service registry |
| [`aegis-config-server`](aegis-config-server/) | 8888 | HTTP Basic-protected, Git-backed Spring Cloud Config server |
| [`aegis-notification-service`](aegis-notification-service/) | 8082 | Notification API, domain lifecycle, PostgreSQL persistence, outbox use case, Kafka consumer, and provider delivery |
| [`aegis-audit-service`](aegis-audit-service/) | 8083 | Kafka audit ingestion, recipient encryption, MongoDB persistence, and audit queries |
| [`aegis-user-service`](aegis-user-service/) | 8084 | User administration against the Keycloak Admin API (list/create/update/disable/reset password); not yet proxied through the gateway |

## HTTP API

All business endpoints require a bearer JWT. Requests sent directly to a domain service are
validated again instead of relying only on gateway security.

### Submit a notification

`POST /api/v1/notifications` — requires `notification:write`.

```bash
curl -i -X POST http://localhost:8080/api/v1/notifications \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "EMAIL",
    "recipient": "user@example.com",
    "templateName": "welcome-email",
    "parameters": {
      "name": "Ada"
    },
    "priority": "HIGH"
  }'
```

Successful response: `202 Accepted`

```json
{
  "id": "2a415e3e-a684-4ad7-897d-490c7c75be91",
  "status": "PENDING"
}
```

The response includes a `Location` header pointing to the status resource. `parameters` defaults
to an empty object and `priority` defaults to `MEDIUM` when omitted.

Supported request values:

| Field | Values or constraint |
| --- | --- |
| `channel` | `EMAIL`, `SMS`, `WHATSAPP`, or `PUSH` |
| `recipient` | Email address for email; E.164 number beginning with `+` for SMS/WhatsApp; non-blank device token for push |
| `templateName` | Active template name, maximum 120 characters |
| `parameters` | JSON object used during template rendering |
| `priority` | `HIGH`, `MEDIUM`, or `LOW` |

At least one active template must be provisioned before a submit request can succeed — see
[Installation, step 6](#6-provision-a-template).

### Query notification status

`GET /api/v1/notifications/{id}/status` — requires `notification:read`.

```bash
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  http://localhost:8080/api/v1/notifications/2a415e3e-a684-4ad7-897d-490c7c75be91/status
```

The response includes the notification identity, channel, recipient, template, current status,
timestamps, and its PostgreSQL-backed notification log.

### Query an audit trail

`GET /api/v1/audit/{notificationId}` — requires `audit:read`.

```bash
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  http://localhost:8083/api/v1/audit/2a415e3e-a684-4ad7-897d-490c7c75be91
```

### Search audit trails

`GET /api/v1/audit` — requires `audit:read`.

Optional query parameters are `channel`, `status`, `from`, `to`, `page`, and `size`. Page numbers
are zero-based, the default size is 20, and the repository adapter caps the effective size at 100.

```bash
curl -G -H "Authorization: Bearer $ACCESS_TOKEN" \
  --data-urlencode "channel=EMAIL" \
  --data-urlencode "status=SENT" \
  --data-urlencode "page=0" \
  --data-urlencode "size=20" \
  http://localhost:8083/api/v1/audit
```

### Manage users

Not yet proxied through the gateway — call `aegis-user-service` directly on port `8084`.

`GET /api/v1/users` (`user:read`), `GET /api/v1/users/{id}` (`user:read`), `POST /api/v1/users`
(`user:admin`), `PUT /api/v1/users/{id}` (`user:admin`), `PATCH /api/v1/users/{id}/status`
(`user:admin`), `PUT /api/v1/users/{id}/password` (`user:admin`).

```bash
curl -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8084/api/v1/users
```

There is no delete endpoint by design — disabling a user via the `status` endpoint is the only
lifecycle mutation available.

## Security

| Component | Behavior |
| --- | --- |
| API Gateway | JWT resource server; enforces per-route OAuth2 scopes (see [`docs/security/scopes.md`](docs/security/scopes.md)); health and info are public |
| Notification service | Stateless JWT resource server; submit requires `notification:write`; status requires `notification:read` |
| Audit service | Stateless JWT resource server; audit queries require `audit:read` |
| User service | Stateless JWT resource server; reads require `user:read`, mutations require `user:admin` (non-hierarchical — `user:admin` does not imply `user:read`) |
| Config Server | HTTP Basic authentication using environment-provided credentials |

The default JWKS URI points to the local Keycloak instance started in
[Installation, step 2](#2-start-keycloak-identity-provider). Supply `JWKS_URI` to use another
compatible OpenID Connect provider.

Actuator `health`, `info`, and `prometheus` endpoints are public across services. Review that
exposure before any non-local deployment.

## Configuration

The most important environment variables are:

| Variable | Default | Used by |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/aegisnotify` | Notification service |
| `DB_USERNAME` / `DB_PASSWORD` | `aegis` / `aegis` | Notification service |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Notification and audit services |
| `MONGODB_URI` | `mongodb://localhost:27017/aegisnotify-audit` | Audit service |
| `AUDIT_ENCRYPTION_KEY` | None outside `local` profile | Audit service |
| `JWKS_URI` | Keycloak realm at `localhost:8088` | Gateway, notification, audit, and user services |
| `KEYCLOAK_BASE_URL` / `KEYCLOAK_REALM` | `http://localhost:8088` / `aegis` | User service |
| `KEYCLOAK_ADMIN_CLIENT_ID` / `KEYCLOAK_ADMIN_CLIENT_SECRET` | `aegis-user-service` / none | User service (Admin API access) |
| `CONFIG_SERVER_USER` / `CONFIG_SERVER_PASSWORD` | None | Config Server |
| `CONFIG_REPO_URI` | None | Config Server Git backend |
| `CONFIG_REPO_USERNAME` / `CONFIG_REPO_TOKEN` | None | Private Config Server Git backend |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | API Gateway |
| `SENDGRID_API_KEY` / `SENDGRID_FROM_ADDRESS` | Empty / `noreply@aegisnotify.com` | Email provider |
| `TWILIO_ACCOUNT_SID` / `TWILIO_AUTH_TOKEN` | Empty | SMS and WhatsApp providers |
| `TWILIO_SMS_FROM_NUMBER` / `TWILIO_WHATSAPP_FROM_NUMBER` | Empty | SMS and WhatsApp providers |
| `FCM_PROJECT_ID` / `FCM_ACCESS_TOKEN` | Empty | Push provider |
| `NOTIFICATION_KAFKA_CONSUMER_ENABLED` | `false` | Notification Kafka listener |
| `NOTIFICATION_KAFKA_RELAY_ENABLED` | `false` | Reserved for the unfinished outbox relay runtime |
| `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD` | `admin` / `admin` | Keycloak container admin console login |

Secondary provider variables follow the same names with a `_SECONDARY_` segment, for example
`SENDGRID_SECONDARY_API_KEY`, `TWILIO_SECONDARY_AUTH_TOKEN`, and `FCM_SECONDARY_ACCESS_TOKEN`. A
starter list of variables also lives in [`.env.example`](.env.example). See
[`aegis-notification-service/src/main/resources/application.yml`](aegis-notification-service/src/main/resources/application.yml)
for the complete topic, provider, and circuit-breaker configuration.

## Testing and quality checks

```bash
# Run all tests and build-time checks (Docker must be running for Testcontainers)
./mvnw clean verify

# Run one module and its required reactor dependencies
./mvnw -pl aegis-notification-service -am test

# Run Checkstyle explicitly
./mvnw checkstyle:check
```

The test suite includes JUnit 5 unit and web-layer tests, Spring context tests, ArchUnit boundary
tests, Kafka integration tests, Prometheus metric tests, and Testcontainers dependencies for
PostgreSQL, Kafka, and MongoDB integration testing. See [`CONTRIBUTING.md`](CONTRIBUTING.md) for
the full checklist CI enforces on every pull request.

## Current implementation status

This repository is an active portfolio project, currently version `0.7.0`.

Stable, source-backed building blocks include the domain models, HTTP contracts, PostgreSQL/Flyway
and MongoDB persistence, audit encryption, Kafka consumers and error handling, provider HTTP
adapters, service discovery, gateway routing with per-route scope enforcement, JWT validation,
Keycloak-backed user administration, metrics, and architecture tests.

The following work remains before the documented asynchronous platform is complete:

- Implement `MessageBrokerPort` for publishing notification outbox events to Kafka.
- Implement `DeadLetterQueuePort` for application-driven critical failures.
- Add a runtime trigger that invokes `PublishOutboxEventUseCase` and honors
  `notification.kafka.relay.enabled`.
- Make the outbox publisher use configured topic names instead of hard-coded defaults.
- Add template provisioning through a migration, administrative API, or operational process.
- Expose or intentionally remove the currently internal cancellation, manual retry, and manual DLT
  use cases.
- Proxy `aegis-user-service` routes through the API Gateway (the scope rules already exist there,
  forward-looking).
- Validate Resilience4j and secondary-provider behavior in an end-to-end provider environment and
  define production thresholds and operational procedures.
- Extend `docker-compose.yml` with PostgreSQL, Kafka, and MongoDB (currently Keycloak-only by
  design) if a one-command environment becomes a goal — see [Project scope](#project-scope).

## Technology stack

- Java 21
- Spring Boot 3.4.1 and Spring Cloud 2024.0.0
- Spring MVC, Spring WebFlux/WebClient, Spring Security, and OAuth2 Resource Server
- Spring Data JPA, PostgreSQL, Flyway, Spring Data MongoDB
- Apache Kafka and Spring Kafka
- Netflix Eureka and Spring Cloud Gateway/Config
- Keycloak (Admin REST API) for identity and user administration
- Micrometer and Prometheus registry
- Resilience4j 2.2.0 for per-channel circuit breakers and secondary-account failover
- JUnit 5, Spring Boot Test, Spring Kafka Test, Testcontainers, and ArchUnit
- Maven Wrapper and Google Checkstyle

## License

This project is part of a personal portfolio. All rights reserved.
