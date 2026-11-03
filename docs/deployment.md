# Deployment and configuration guide

This guide explains how to bring up the full AegisNotify stack from scratch, what infrastructure
it needs, and every environment variable and key property each service reads. It was written from
the Compose files, `application*.yml` files, Dockerfiles, and frontend sources in this repository
(issue #196). Where the code and another document disagree, this guide states what the code does.

## Navigate

- [Topology and ports](#topology-and-ports)
- [Required infrastructure](#required-infrastructure)
- [Compose files](#compose-files)
- [Startup order](#startup-order)
- [Bring up the full stack from scratch](#bring-up-the-full-stack-from-scratch)
- [Run services on the host instead](#run-services-on-the-host-instead)
- [Configuration reference](#configuration-reference)
- [Admin frontend runtime configuration](#admin-frontend-runtime-configuration)
- [Secrets handling](#secrets-handling)
- [Dev-only defaults to change in production](#dev-only-defaults-to-change-in-production)

## Topology and ports

| Component | Port | Notes |
| --- | ---: | --- |
| `aegis-eureka-server` | 8761 | Service registry; HTTP Basic protected |
| `aegis-config-server` | 8888 | Spring Cloud Config; HTTP Basic protected |
| `aegis-api-gateway` | 8080 | Only public API entry point |
| `aegis-notification-service` | 8082 | PostgreSQL, Kafka |
| `aegis-audit-service` | 8083 | MongoDB, Kafka |
| `aegis-user-service` | 8084 | Calls the Keycloak Admin API |
| `aegis-admin-frontend` | 4200 (dev server) | Angular SPA, served from the host, not from Compose |
| Keycloak | 8088 (container 8080) | Realm `aegis`, imported from `docker/keycloak/aegis-realm.json` |
| PostgreSQL | 5432 | Database `aegisnotify` |
| MongoDB | 27017 | Database `aegisnotify-audit` |
| Kafka | 9092 (host), 29092 (Compose network) | Single node, KRaft mode, no Zookeeper |
| Redis | 6379 | Used by the gateway rate limiter |

In `docker-compose.full.yml` every published port is bound to `127.0.0.1`, except Keycloak, which
is published by `docker-compose.yml` as `8088:8080`.

## Required infrastructure

| Dependency | Needed by | Provided by Compose as |
| --- | --- | --- |
| PostgreSQL | notification service (Flyway creates the schema on first start) | `postgres:16-alpine` |
| MongoDB | audit service | `mongo:7`, no authentication |
| Kafka | notification and audit services | `confluentinc/cp-kafka:7.6.1`, KRaft, single node |
| Redis | API gateway (`RequestRateLimiter` on `POST /api/v1/notifications`) | `redis:7-alpine`, no authentication |
| Keycloak | gateway and services (JWKS), user service (Admin API), frontend (login) | `quay.io/keycloak/keycloak:26.0`, `start-dev --import-realm` |
| Eureka | every service except itself | `aegis-eureka-server` |

## Compose files

| File | Purpose |
| --- | --- |
| `docker-compose.yml` | Keycloak only. Mounts `docker/keycloak` for the realm import and `docker/keycloak/themes/aegis` as the `aegis` login theme. Has no health check. |
| `docker-compose.full.yml` | Full stack. Pulls Keycloak in with `include: docker-compose.yml` (do not also pass `-f docker-compose.yml`), and adds PostgreSQL, MongoDB, Kafka, Redis, and the six Spring Boot services. Defines no Compose profiles. |

Service images are built from the repository root (`context: .`) using each module's `Dockerfile`:
a multi-stage build (`eclipse-temurin:21-jdk-alpine` to build, `eclipse-temurin:21-jre-alpine` to
run, non-root user). Tests and Checkstyle are skipped inside the image build.

Named volumes: `aegis-postgres-data`, `aegis-mongo-data`, `aegis-kafka-data`. Keycloak has no
volume; the `aegis` realm is re-imported from the JSON file whenever its container is recreated.

Variables Compose substitutes from the shell or from a `.env` file next to the Compose file (see
[`.env.example`](../.env.example) for the repository's starter list): `KC_BOOTSTRAP_ADMIN_USERNAME`,
`KC_BOOTSTRAP_ADMIN_PASSWORD`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`,
`EUREKA_SECURITY_USER`, `EUREKA_SECURITY_PASSWORD`, `CONFIG_SERVER_USER`, `CONFIG_SERVER_PASSWORD`,
`KEYCLOAK_ADMIN_CLIENT_SECRET`, `SENDGRID_API_KEY`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`,
`FCM_PROJECT_ID`, `FCM_ACCESS_TOKEN`. Defaults are listed in the
[Compose variables](#compose-variables) table.

## Startup order

Compose enforces this order with health checks (`depends_on`):

1. **Infrastructure**: PostgreSQL, MongoDB, Kafka, Redis (health-checked) and Keycloak (started, not
   health-checked).
2. **`aegis-eureka-server`**: no external dependency. Health-checked via `/actuator/health`.
3. **`aegis-config-server`**: waits for Eureka to be healthy.
4. **Services**: `aegis-api-gateway` (waits for Config Server, Redis, Keycloak),
   `aegis-notification-service` (Config Server, PostgreSQL, Kafka, Keycloak),
   `aegis-audit-service` (Config Server, MongoDB, Kafka, Keycloak),
   `aegis-user-service` (Config Server, Keycloak). They start in parallel once their own
   dependencies are healthy.
5. **Frontend**: started by hand on the host after the gateway and Keycloak are up.

The gateway routes with `lb://` URIs, so Eureka must be reachable and the target services
registered before routed calls succeed.

## Bring up the full stack from scratch

Prerequisites: JDK 21 (only needed for host runs), Docker with Compose v2 (the `include:` feature),
Node.js with `pnpm` for the frontend.

1. Clone the repository.
2. Optional: create a `.env` file next to `docker-compose.full.yml` to override defaults,
   especially the provider credentials. Do not commit it.
3. Build and start everything:

   ```bash
   docker compose -f docker-compose.full.yml up -d --build
   docker compose -f docker-compose.full.yml ps
   ```

   The first build compiles all modules inside Docker and takes several minutes. Wait until the
   `ps` output shows every application container as `healthy` (each has a 60 s start period and up
   to 12 retries at 10 s intervals).
4. Start the admin frontend on the host:

   ```bash
   cd aegis-admin-frontend
   pnpm install
   pnpm start
   ```

   Open `http://localhost:4200`. Local login (realm import): user `aegis-dev`, password `dev123`.
5. Provision at least one active template before submitting notifications; the notification service
   has no template seed migration. See step 4 of the Installation section in the
   [README](../README.md#4-provision-a-template).
6. Get a token and call the API as shown in the
   [README](../README.md#7-get-a-token-and-call-the-api).

Provider credentials default to the literal `local-placeholder`, so the notification service
starts, but real deliveries fail against the vendor APIs until real values are supplied in `.env`.

To tear down and delete all data volumes:

```bash
docker compose -f docker-compose.full.yml down -v
```

### Infrastructure only

To run the Spring Boot services on the host with Maven, start only the infrastructure and name the
services explicitly (a bare `up -d` would also start the six application containers):

```bash
docker compose -f docker-compose.full.yml up -d keycloak postgres mongo kafka redis
```

If you only need Keycloak, `docker compose up -d keycloak` uses `docker-compose.yml` alone.

## Run services on the host instead

Follow the [README installation steps 2 to 6](../README.md#2-start-keycloak-identity-provider).
Points specific to host runs, from the `application.yml` files:

- Every Eureka client reads `EUREKA_SECURITY_USER` and `EUREKA_SECURITY_PASSWORD` without a
  default, so export the same values for Eureka and for every client.
- `AUDIT_ENCRYPTION_KEY` has no default in `application.yml`; the `local` profile supplies a
  development-only fallback.
- `KEYCLOAK_ADMIN_CLIENT_SECRET` has no default in the user service's `application.yml`.
- The `local` profile additionally lowers Kafka topic replication to 1 (notification service) and
  enables Swagger UI / OpenAPI docs (notification, audit, user services).
- The notification service does not import the Config Server (`application.yml` has no
  `spring.config.import`); the gateway, audit, and user services import it as `optional:`.
- The Config Server, outside Compose, uses its Git backend and requires `CONFIG_REPO_URI`,
  `CONFIG_REPO_USERNAME`, and `CONFIG_REPO_TOKEN` (no defaults; `clone-on-start: true`, default
  label `main`). In Compose it runs with the `native` profile against `docker/config-repo`
  (see [`docker/config-repo/README.md`](../docker/config-repo/README.md)), which is intentionally
  empty.

## Configuration reference

Conventions: "Default" is the fallback in `application.yml` (`${VAR:default}`); "none" means the
variable has no default and the service needs it (or the corresponding feature is unusable);
"empty" means the default is an empty string. "Secret" is `yes` for credentials, tokens, and keys.
Complete property trees live in each module's `application.yml`.

### Compose variables

These are substituted by `docker-compose.full.yml` / `docker-compose.yml`.

| Variable | Compose default | Purpose | Secret |
| --- | --- | --- | --- |
| `KC_BOOTSTRAP_ADMIN_USERNAME` | `admin` | Keycloak admin console user | no |
| `KC_BOOTSTRAP_ADMIN_PASSWORD` | `admin` | Keycloak admin console password | yes |
| `POSTGRES_DB` | `aegisnotify` | PostgreSQL database name; also feeds the notification service `DB_URL` | no |
| `POSTGRES_USER` | `aegis` | PostgreSQL user; also feeds `DB_USERNAME` | no |
| `POSTGRES_PASSWORD` | `aegis` | PostgreSQL password; also feeds `DB_PASSWORD` | yes |
| `EUREKA_SECURITY_USER` | `eureka` | Eureka Basic-auth user, shared with all clients | no |
| `EUREKA_SECURITY_PASSWORD` | `eureka` | Eureka Basic-auth password, shared with all clients | yes |
| `CONFIG_SERVER_USER` | `config` | Config Server Basic-auth user; clients receive it as `SPRING_CLOUD_CONFIG_USERNAME` | no |
| `CONFIG_SERVER_PASSWORD` | `config` | Config Server Basic-auth password; clients receive it as `SPRING_CLOUD_CONFIG_PASSWORD` | yes |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | `local-dev-only-secret` | Secret of the `aegis-user-service` Keycloak client; must match the realm | yes |
| `SENDGRID_API_KEY` | `local-placeholder` | See notification service table | yes |
| `TWILIO_ACCOUNT_SID` | `local-placeholder` | See notification service table | yes |
| `TWILIO_AUTH_TOKEN` | `local-placeholder` | See notification service table | yes |
| `FCM_PROJECT_ID` | `local-placeholder` | See notification service table | no |
| `FCM_ACCESS_TOKEN` | `local-placeholder` | See notification service table | yes |

Fixed by `docker-compose.full.yml` for the containers (not overridable through `.env`):
`EUREKA_HOST=aegis-eureka-server`, `SPRING_CLOUD_CONFIG_URI=http://aegis-config-server:8888`,
`JWKS_URI=http://keycloak:8080/realms/aegis/protocol/openid-connect/certs`, `REDIS_HOST=redis`,
`KAFKA_BOOTSTRAP_SERVERS=kafka:29092`, `MONGODB_URI=mongodb://mongo:27017/aegisnotify-audit`,
`KEYCLOAK_BASE_URL=http://keycloak:8080`, `KEYCLOAK_REALM=aegis`,
`NOTIFICATION_KAFKA_RELAY_ENABLED=true`, `NOTIFICATION_KAFKA_CONSUMER_ENABLED=true`,
`SPRING_PROFILES_ACTIVE=local` (notification and audit) or `native` (Config Server).

### Eureka server (`aegis-eureka-server`)

| Variable | Default | Purpose | Secret |
| --- | --- | --- | --- |
| `EUREKA_SECURITY_USER` | none | HTTP Basic user for the dashboard and registry API | no |
| `EUREKA_SECURITY_PASSWORD` | none | HTTP Basic password | yes |

Fixed properties: port `8761`, `register-with-eureka: false`, `fetch-registry: false`,
`enable-self-preservation: false`, eviction interval 5000 ms; actuator exposes `health,info`.

### Config Server (`aegis-config-server`)

| Variable | Default | Purpose | Secret |
| --- | --- | --- | --- |
| `CONFIG_SERVER_USER` | none | HTTP Basic user | no |
| `CONFIG_SERVER_PASSWORD` | none | HTTP Basic password | yes |
| `CONFIG_REPO_URI` | none | Git repository served as configuration (not used in the Compose `native` profile) | no |
| `CONFIG_REPO_USERNAME` | none | Git username | no |
| `CONFIG_REPO_TOKEN` | none | Git token / password | yes |
| `EUREKA_SECURITY_USER` / `EUREKA_SECURITY_PASSWORD` | none | Credentials embedded in the Eureka registration URL | user no / password yes |
| `EUREKA_HOST` | `localhost` | Host of the Eureka server | no |
| `SPRING_PROFILES_ACTIVE` | unset | Set to `native` by Compose | no |
| `SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS` | unset | Set to `file:/config-repo` by Compose | no |

Fixed: port `8888`, Git `default-label: main`, `clone-on-start: true`; registers with Eureka.

### API Gateway (`aegis-api-gateway`)

| Variable | Default | Purpose | Secret |
| --- | --- | --- | --- |
| `JWKS_URI` | `http://localhost:8088/realms/aegis/protocol/openid-connect/certs` | Keycloak JWKS endpoint for JWT validation | no |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Allowed CORS origin. The code wraps the value in a single-element list, so provide one origin (not a comma-separated list) | no |
| `REDIS_HOST` | `localhost` | Redis host for rate limiting | no |
| `REDIS_PORT` | `6379` | Redis port | no |
| `REDIS_TIMEOUT` | `250ms` | Redis command timeout | no |
| `REDIS_CONNECT_TIMEOUT` | `500ms` | Redis connect timeout | no |
| `RATE_LIMIT_REPLENISH_RATE` | `100` | Token replenish rate for `POST /api/v1/notifications` | no |
| `RATE_LIMIT_BURST_CAPACITY` | `1200` | Bucket capacity | no |
| `RATE_LIMIT_REQUESTED_TOKENS` | `60` | Tokens consumed per request (defaults give about 100 requests per minute sustained, burst of 20, per the comments in `application.yml`) | no |
| `EUREKA_SECURITY_USER` / `EUREKA_SECURITY_PASSWORD` | none | Eureka credentials | user no / password yes |
| `EUREKA_HOST` | `localhost` | Eureka host | no |

Fixed: port `8080`; actuator exposes `health,info,prometheus`. Routes defined in `application.yml`
target `aegis-notification-service`, `aegis-user-service`, and `aegis-audit-service` through Eureka.
API-docs routes under `/docs/<service>` are defined in `ApiDocsConfig` and only work when the
target services run with the `local` profile.

### Notification service (`aegis-notification-service`)

Infrastructure and security:

| Variable | Default | Purpose | Secret |
| --- | --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/aegisnotify` | JDBC URL | no |
| `DB_USERNAME` | `aegis` | Database user | no |
| `DB_PASSWORD` | `aegis` | Database password | yes |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers | no |
| `JWKS_URI` | `http://localhost:8088/realms/aegis/protocol/openid-connect/certs` | JWT validation keys | no |
| `EUREKA_SECURITY_USER` / `EUREKA_SECURITY_PASSWORD` | none | Eureka credentials | user no / password yes |
| `EUREKA_HOST` | `localhost` | Eureka host | no |
| `AEGIS_DEMO_METRICS_ENABLED` | `false` | Seeds demo Micrometer meters for the admin metrics screen; local demo only | no |

Template cache and aggregation:

| Variable | Default | Purpose | Secret |
| --- | --- | --- | --- |
| `NOTIFICATION_TEMPLATE_CACHE_MAX_SIZE` | `500` | Template cache maximum entries | no |
| `NOTIFICATION_TEMPLATE_CACHE_TTL` | `PT1H` | Template cache expire-after-write | no |
| `NOTIFICATION_AGGREGATION_ENABLED` | `false` | Enables notification aggregation | no |
| `NOTIFICATION_AGGREGATION_WINDOW` | `PT5M` | Aggregation window | no |
| `NOTIFICATION_AGGREGATION_POLL_INTERVAL` | `PT10S` | Aggregation poll interval | no |
| `NOTIFICATION_AGGREGATION_REQUIRE_SAME_TEMPLATE` | `true` | Group only notifications sharing a template | no |
| `NOTIFICATION_AGGREGATION_MAX_GROUP_SIZE` | `20` | Maximum notifications per group | no |
| `NOTIFICATION_AGGREGATION_CLAIM_LEASE` | `PT2M` | Claim lease duration | no |
| `NOTIFICATION_AGGREGATION_MAX_ATTEMPTS` | `3` | Maximum summarization attempts | no |
| `NOTIFICATION_AGGREGATION_EXCLUDED_TEMPLATES` | empty | Templates excluded from aggregation | no |
| `NOTIFICATION_AGGREGATION_EXCLUDED_CHANNELS` | empty | Channels excluded from aggregation | no |
| `AGGREGATION_LLM_VENDOR` | `anthropic` | Summarizer vendor: `anthropic` or `groq` (see [ADR 0006](adr/0006-alternate-llm-summarizer-for-aggregation.md)) | no |
| `AGGREGATION_LLM_BASE_URL` | `https://api.anthropic.com` | Anthropic API base URL | no |
| `AGGREGATION_LLM_API_KEY` | empty | Anthropic API key | yes |
| `AGGREGATION_LLM_MODEL` | `claude-sonnet-4-5` | Anthropic model name | no |
| `AGGREGATION_LLM_API_VERSION` | `2023-06-01` | Anthropic API version header | no |
| `AGGREGATION_LLM_MAX_TOKENS` | `512` | Maximum output tokens | no |
| `AGGREGATION_LLM_TIMEOUT` | `PT10S` | Response timeout | no |
| `AGGREGATION_LLM_MAX_OUTPUT_CHARS` | `2000` | Maximum summary length | no |
| `AGGREGATION_LLM_GROQ_BASE_URL` | `https://api.groq.com/openai` | Groq base URL | no |
| `AGGREGATION_LLM_GROQ_API_KEY` | empty | Groq API key | yes |
| `AGGREGATION_LLM_GROQ_MODEL` | `llama-3.3-70b-versatile` | Groq model name | no |

Kafka and outbox:

| Variable | Default | Purpose | Secret |
| --- | --- | --- | --- |
| `NOTIFICATION_KAFKA_RELAY_ENABLED` | `false` | Outbox relay switch (Compose sets `true`) | no |
| `NOTIFICATION_KAFKA_RELAY_POLL_INTERVAL` | `PT5S` | Outbox relay poll interval | no |
| `NOTIFICATION_KAFKA_CONSUMER_ENABLED` | `false` | Enables the Kafka listener (Compose sets `true`) | no |
| `NOTIFICATION_KAFKA_CONSUMER_GROUP_ID` | `notification-service` | Consumer group | no |
| `NOTIFICATION_KAFKA_HIGH_PRIORITY_TOPIC` | `high-priority-topic` | High priority topic | no |
| `NOTIFICATION_KAFKA_MEDIUM_PRIORITY_TOPIC` | `medium-priority-topic` | Medium priority topic | no |
| `NOTIFICATION_KAFKA_LOW_PRIORITY_TOPIC` | `low-priority-topic` | Low priority topic | no |
| `NOTIFICATION_KAFKA_TOPIC_PARTITIONS` | `3` | Partitions per topic | no |
| `NOTIFICATION_KAFKA_TOPIC_REPLICATION_FACTOR` | `3` | Replication factor (the `local` profile sets 1) | no |
| `NOTIFICATION_KAFKA_TOPIC_MIN_IN_SYNC_REPLICAS` | `2` | Min in-sync replicas (the `local` profile sets 1) | no |
| `NOTIFICATION_KAFKA_DLT_SUFFIX` | `-dlt` | Dead-letter topic suffix | no |
| `NOTIFICATION_KAFKA_DLQ_TOPIC` | `notifications-dlq` | Dead-letter queue topic | no |

Providers (primary; the `*_SECONDARY_*` rows are the failover provider):

| Variable | Default | Purpose | Secret |
| --- | --- | --- | --- |
| `NOTIFICATION_EMAIL_PROVIDER` | `sendgrid` | Email vendor: `sendgrid` or `mailtrap` | no |
| `SENDGRID_BASE_URL` | `https://api.sendgrid.com` | Email API base URL | no |
| `SENDGRID_API_KEY` | empty | Email API key | yes |
| `SENDGRID_FROM_ADDRESS` | `noreply@aegisnotify.com` | Email sender address | no |
| `SENDGRID_SECONDARY_BASE_URL` | `https://api.sendgrid.com` | Secondary email API base URL | no |
| `SENDGRID_SECONDARY_API_KEY` | empty | Secondary email API key | yes |
| `SENDGRID_SECONDARY_FROM_ADDRESS` | `noreply@aegisnotify.com` | Secondary sender address | no |
| `TWILIO_BASE_URL` | `https://api.twilio.com` | Twilio base URL (SMS and WhatsApp) | no |
| `TWILIO_ACCOUNT_SID` | empty | Twilio account SID | yes |
| `TWILIO_AUTH_TOKEN` | empty | Twilio auth token | yes |
| `TWILIO_SMS_FROM_NUMBER` | empty | SMS sender number | no |
| `TWILIO_WHATSAPP_FROM_NUMBER` | empty | WhatsApp sender number | no |
| `TWILIO_WHATSAPP_CONTENT_SID` | empty | Twilio WhatsApp content SID | no |
| `TWILIO_SECONDARY_BASE_URL` | `https://api.twilio.com` | Secondary Twilio base URL | no |
| `TWILIO_SECONDARY_ACCOUNT_SID` | empty | Secondary Twilio account SID | yes |
| `TWILIO_SECONDARY_AUTH_TOKEN` | empty | Secondary Twilio auth token | yes |
| `TWILIO_SECONDARY_SMS_FROM_NUMBER` | empty | Secondary SMS sender | no |
| `TWILIO_SECONDARY_WHATSAPP_FROM_NUMBER` | empty | Secondary WhatsApp sender | no |
| `TWILIO_SECONDARY_WHATSAPP_CONTENT_SID` | empty | Secondary WhatsApp content SID | no |
| `NOTIFICATION_WHATSAPP_PROVIDER` | `twilio` | WhatsApp vendor: `twilio` or `vonage` | no |
| `VONAGE_WHATSAPP_BASE_URL` | `https://messages-sandbox.nexmo.com` | Vonage base URL (sandbox by default) | no |
| `VONAGE_WHATSAPP_API_KEY` | empty | Vonage API key | yes |
| `VONAGE_WHATSAPP_API_SECRET` | empty | Vonage API secret | yes |
| `VONAGE_WHATSAPP_FROM_NUMBER` | empty | Vonage sender | no |
| `VONAGE_SECONDARY_WHATSAPP_BASE_URL` | `https://messages-sandbox.nexmo.com` | Secondary Vonage base URL | no |
| `VONAGE_SECONDARY_WHATSAPP_API_KEY` | empty | Secondary Vonage API key | yes |
| `VONAGE_SECONDARY_WHATSAPP_API_SECRET` | empty | Secondary Vonage API secret | yes |
| `VONAGE_SECONDARY_WHATSAPP_FROM_NUMBER` | empty | Secondary Vonage sender | no |
| `FCM_BASE_URL` | `https://fcm.googleapis.com` | Push API base URL | no |
| `FCM_PROJECT_ID` | empty | Firebase project ID | no |
| `FCM_ACCESS_TOKEN` | empty | FCM access token | yes |
| `FCM_SECONDARY_BASE_URL` | `https://fcm.googleapis.com` | Secondary push base URL | no |
| `FCM_SECONDARY_PROJECT_ID` | empty | Secondary Firebase project | no |
| `FCM_SECONDARY_ACCESS_TOKEN` | empty | Secondary FCM token | yes |

Compose comments state that `ProviderConfig` fails fast on blank primary credentials, which is why
Compose supplies `local-placeholder`.

Key fixed properties (edit `application.yml` or use the Config Server to change them): port `8082`;
`ddl-auto: validate` with Flyway enabled; audit publishing enabled on topic
`notification-audit-events`; Resilience4j circuit breakers `email-provider`, `sms-provider`,
`whatsapp-provider`, `push-provider`, and `aggregation-agent` (sliding window 10, failure rate 50 %,
slow-call rate 40 %, slow-call threshold 2000 ms, 6000 ms for `aggregation-agent`, 60 s open wait,
3 half-open calls); retry max 3 attempts, 200 ms exponential backoff, on the four provider
instances; actuator exposes `health,info,prometheus,circuitbreakers`. Swagger UI and OpenAPI docs
are disabled unless the `local` profile is active.

### Audit service (`aegis-audit-service`)

| Variable | Default | Purpose | Secret |
| --- | --- | --- | --- |
| `AUDIT_ENCRYPTION_KEY` | none (the `local` profile provides a dev-only fallback) | Base64 AES key used to encrypt recipients at rest; startup fails if unset outside `local` | yes |
| `MONGODB_URI` | `mongodb://localhost:27017/aegisnotify-audit` | MongoDB connection string (may embed credentials if authentication is added) | yes if it contains credentials |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers | no |
| `JWKS_URI` | `http://localhost:8088/realms/aegis/protocol/openid-connect/certs` | JWT validation keys | no |
| `EUREKA_SECURITY_USER` / `EUREKA_SECURITY_PASSWORD` | none | Eureka credentials | user no / password yes |
| `EUREKA_HOST` | `localhost` | Eureka host | no |

Fixed: port `8083`; topic `notification-audit-events`; consumer group `audit-service`; actuator
exposes `health,info,prometheus`.

### User service (`aegis-user-service`)

| Variable | Default | Purpose | Secret |
| --- | --- | --- | --- |
| `KEYCLOAK_BASE_URL` | `http://localhost:8088` | Keycloak base URL for the Admin API | no |
| `KEYCLOAK_REALM` | `aegis` | Realm | no |
| `KEYCLOAK_ADMIN_CLIENT_ID` | `aegis-user-service` | Confidential client used for the Admin API | no |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | none | Client secret for that client | yes |
| `KEYCLOAK_ADMIN_TIMEOUT` | `5s` | Admin API timeout | no |
| `JWKS_URI` | `http://localhost:8088/realms/aegis/protocol/openid-connect/certs` | JWT validation keys | no |
| `EUREKA_SECURITY_USER` / `EUREKA_SECURITY_PASSWORD` | none | Eureka credentials | user no / password yes |
| `EUREKA_HOST` | `localhost` | Eureka host | no |

Fixed: port `8084`; actuator exposes `health,info,prometheus`.

### Config client variables (set by Compose)

`SPRING_CLOUD_CONFIG_URI`, `SPRING_CLOUD_CONFIG_USERNAME`, and `SPRING_CLOUD_CONFIG_PASSWORD` are
Spring Cloud Config client settings that Compose injects into the application containers (defaults
via `CONFIG_SERVER_USER` / `CONFIG_SERVER_PASSWORD`). They have no entry in any `application.yml`;
the password is a secret.

### Keycloak realm (`docker/keycloak/aegis-realm.json`)

Not environment variables, but configuration that must be consistent with the above: realm
`aegis`; scopes `notification:write`, `notification:read`, `audit:read`, `user:read`,
`user:admin`; clients `aegis-dev-cli` (public, password grant, local development), `aegis-admin-frontend`
(public, authorization code with PKCE, redirect URIs and web origin `http://localhost:4200`), and
`aegis-user-service` (confidential). The test user `aegis-dev` is for local development only. See
[`security/scopes.md`](security/scopes.md).

## Admin frontend runtime configuration

Status on this branch: implemented (issue #171). Source: `aegis-admin-frontend/src/app/core/config/runtime-config.ts`,
`src/main.ts`, `public/config.json`, `public/serve.json`.

How it works:

- Before Angular and Keycloak start, the browser fetches `config.json` from the application's base
  URL (`new URL('config.json', document.baseURI)`), with `cache: 'no-store'` and a 5 second timeout.
- The file shipped in the repository (`aegis-admin-frontend/public/config.json`) contains `{}`, so
  the compiled defaults apply.
- Recognized keys:

  | Key | Default (from `src/environments/environment*.ts`) | Purpose |
  | --- | --- | --- |
  | `apiBaseUrl` | `http://localhost:8080` | Gateway base URL, without the `/api/v1` suffix |
  | `keycloakUrl` | `http://localhost:8088` | Keycloak base URL used for login |

- Values must be absolute `http`/`https` URLs without embedded credentials, query string, or
  fragment; otherwise that key falls back to its default. Trailing slashes are removed.
- A 404, unreachable file, non-OK response, invalid JSON, or non-object JSON falls back to the
  defaults (a console warning is logged except for 404).
- The Keycloak realm (`aegis`) and client ID (`aegis-admin-frontend`) are not runtime-configurable;
  they come from the environment files at build time.
- `config.json` is public. Put only URLs in it, never credentials or tokens. The URLs must be
  reachable from the user's browser (not private Docker service names).
- To deploy one build to several environments, replace `dist/aegis-admin-frontend/browser/config.json`
  after `pnpm build`. Angular does not read server environment variables; if you want to drive the
  file from deployment variables, generate it with a JSON serializer at deploy time (the repository
  ships no such generator or frontend container image).
- Serve `config.json` with `Cache-Control: no-store`. `public/serve.json` does this for
  `npx serve dist/aegis-admin-frontend/browser`, and excludes `config.json` and
  `silent-check-sso.html` from the SPA rewrite. Other web servers need an equivalent rule.
- For a deployed frontend origin, also set the gateway `CORS_ALLOWED_ORIGINS` to that origin and
  add the origin to the `aegis-admin-frontend` client's redirect URIs and web origins in Keycloak.

Details and examples: [`aegis-admin-frontend/README.md`](../aegis-admin-frontend/README.md).

## Secrets handling

- No real secret values are committed. The repository contains only local-development defaults
  (listed below) and empty or `local-placeholder` provider values.
- Supply secrets as environment variables at process or container start: from your shell, a CI/CD
  secret store, or an untracked `.env` file that Compose reads. Never commit a `.env` file.
- In `application.yml`, secrets are read through `${VAR}` placeholders. Variables with no default
  (`EUREKA_SECURITY_PASSWORD`, `CONFIG_SERVER_PASSWORD`, `AUDIT_ENCRYPTION_KEY`,
  `KEYCLOAK_ADMIN_CLIENT_SECRET`, `CONFIG_REPO_TOKEN`) make the service fail at startup when missing, which
  is intended.
- Secrets in this guide are marked `yes` in the tables above: database passwords, Eureka and Config
  Server passwords, the Keycloak admin and client secrets, `AUDIT_ENCRYPTION_KEY`, all provider API
  keys/tokens/auth tokens/secrets, LLM API keys, and `CONFIG_REPO_TOKEN`.
- Rotating `KEYCLOAK_ADMIN_CLIENT_SECRET` requires changing it in Keycloak and in the environment;
  for it to survive a realm re-import, also change `docker/keycloak/aegis-realm.json`.
- The frontend `config.json` is served publicly and must not hold secrets.
- Actuator `health`, `info`, and `prometheus` are public across services; review that exposure
  before any non-local deployment (see the README Security section).

## Dev-only defaults to change in production

| Item | Where | Why it must change |
| --- | --- | --- |
| `admin` / `admin` Keycloak admin login | `KC_BOOTSTRAP_ADMIN_*` defaults | Well-known credentials |
| `aegis-dev` user, `aegis-dev-cli` client | `docker/keycloak/aegis-realm.json` | Local development only; the realm file marks them as such |
| `local-dev-only-secret` | `KEYCLOAK_ADMIN_CLIENT_SECRET` Compose default and realm file | Public, committed value |
| Keycloak `start-dev` | `docker-compose.yml` | Development mode; not a production start command |
| `aegis` / `aegis` database credentials | `DB_*` / `POSTGRES_*` defaults | Weak defaults |
| `eureka` / `eureka`, `config` / `config` | Compose defaults | Weak defaults |
| `local` profile `AUDIT_ENCRYPTION_KEY` fallback | `application-local.yml` | Committed key; production must provide its own |
| `local` profile itself | Compose sets it for notification and audit | Enables Swagger UI / OpenAPI docs and single-replica Kafka settings |
| MongoDB and Redis without authentication, Kafka PLAINTEXT, single node | `docker-compose.full.yml` | Local development only |
| Kafka replication 3 / min ISR 2 | notification defaults | Require at least three brokers outside `local` |
| `local-placeholder` provider values | Compose defaults | Deliveries fail until real credentials are set |
| `AEGIS_DEMO_METRICS_ENABLED` | notification service | Keep `false` so metrics reflect real traffic |
| Keycloak redirect URIs / web origins `http://localhost:4200`, `CORS_ALLOWED_ORIGINS` default | realm file, gateway | Must match the real frontend origin |
| Vonage sandbox URL | `VONAGE_*_BASE_URL` defaults | Sandbox endpoint |
