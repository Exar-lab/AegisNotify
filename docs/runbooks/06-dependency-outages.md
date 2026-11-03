# Runbook: dependency outages (Keycloak, Eureka, Config Server)

## Background

| Dependency | Who uses it | Used for | Config |
| --- | --- | --- | --- |
| Keycloak (realm `aegis`, host port 8088) | Gateway, notification, audit, user services (JWT validation); admin frontend (login); user service (Admin API) | Issuing tokens and publishing signing keys | `JWKS_URI` (default `http://localhost:8088/realms/aegis/protocol/openid-connect/certs`; `docker-compose.full.yml` uses `http://keycloak:8080/...`) |
| Eureka (`aegis-eureka-server`, 8761) | Gateway (register and fetch registry), notification service and others (register), Config Server (register) | Service discovery for the gateway's `lb://` routes | `EUREKA_HOST`, `EUREKA_SECURITY_USER`, `EUREKA_SECURITY_PASSWORD` |
| Config Server (`aegis-config-server`, 8888) | **Gateway, audit service, user service only** | Optional centralized configuration | `spring.config.import: optional:configserver:` |

Facts from the configuration that determine the blast radius:

- **The notification service does not use Config Server.** It has no `spring-cloud-starter-config`
  dependency and no `spring.config.import`. Its outbox relay, Kafka consumer and provider delivery
  are unaffected by a Config Server outage. The compose file still gates its container start on the
  Config Server health check, which affects only `docker compose up` ordering.
- Gateway, audit and user services import Config Server as **`optional:`**, so they start without
  it and fall back to their own `application.yml`. No `fail-fast` is configured.
- None of the services exposes a config refresh endpoint (`management.endpoints.web.exposure.include`
  is `health,info,prometheus` on gateway, audit, user; `health,info,prometheus,circuitbreakers` on
  notification; `health,info` on Eureka and Config Server). Configuration changes only take effect on
  restart.
- The delivery pipeline (outbox relay, Kafka consumer, provider calls) never calls Keycloak, Eureka,
  or Config Server. Only the HTTP API and the admin UI depend on them.
- Actuator `health`, `info` and `prometheus` are public on the notification service, so monitoring
  keeps working during a Keycloak outage.
- Eureka runs with `enable-self-preservation: false` and `eviction-interval-timer-in-ms: 5000`;
  it does not register with itself or fetch a registry.
- The compose Keycloak runs `start-dev --import-realm` with no healthcheck and no data volume. State
  created at runtime (for example users made through `aegis-user-service`) is not declared
  persistent; the realm is re-imported from `docker/keycloak/aegis-realm.json` on a fresh container.

Behavior of Spring Security, Spring Cloud and the Eureka client during an outage is framework
behavior and was **not exercised in this repository's tests**; the expectations below are labelled
accordingly and should be confirmed in your environment.

## Keycloak unavailable

### Symptom

- Admin UI login redirects fail or hang; users cannot sign in or refresh tokens.
- API calls with a token that cannot be validated are rejected (401, or a server error if the key
  set cannot be fetched; the exact status is not tested here).
- `aegis-user-service` operations (list, create, disable, reset password) fail because they call
  the Keycloak Admin API.
- Notifications already accepted continue to be relayed, processed and delivered.

### Detection

| Signal | Where |
| --- | --- |
| OIDC discovery | `curl -s http://localhost:8088/realms/aegis/.well-known/openid-configuration` (no response or non-200 means down) |
| Signing keys | `curl -s http://localhost:8088/realms/aegis/protocol/openid-connect/certs` |
| Container | `docker compose ps keycloak` and `docker logs aegis-keycloak` (compose service name `keycloak`, container `aegis-keycloak`) |
| Service symptoms | Gateway and service logs showing JWT or JWK retrieval errors; a spike in 401 responses at the gateway |
| Unaffected | `GET :8082/actuator/health`, `/actuator/prometheus`; delivery counters keep moving |

Keycloak has `KC_HEALTH_ENABLED=true` in the compose file but its health port is not published, and
the compose file defines no healthcheck for it; use the discovery URL above instead.

### Diagnosis

1. Is the container running? `docker compose ps`, then the logs for startup errors (realm import
   failures, port 8088 already in use).
2. Is it reachable from where the services run? In the full compose stack the services use
   `http://keycloak:8080`; on the host, `http://localhost:8088`. A wrong `JWKS_URI` produces the same
   symptoms as an outage.
3. Is only one consumer failing? If discovery works from the host but a service fails, check that
   service's `JWKS_URI` and network.
4. Realm content: if Keycloak restarted from a fresh container, confirm the `aegis` realm, clients
   (`aegis-dev-cli`, `aegis-user-service`), scopes and the test user exist. Users added at runtime
   may be gone.

### Remediation

1. Restart: `docker compose up -d keycloak` (or the full stack file). The realm is imported from
   `docker/keycloak/aegis-realm.json` via `--import-realm`.
2. After recovery, obtain a new token (see the root README, "Get a token and call the API") and
   confirm `GET /api/v1/notifications` returns 200 through the gateway.
3. Recreate runtime-created users. If the confidential client secret was rotated in the console, update
   `KEYCLOAK_ADMIN_CLIENT_SECRET` for `aegis-user-service` and restart that service; the realm file
   ships `local-dev-only-secret`.
4. Services need no restart for a Keycloak restart in normal conditions (keys are fetched from the
   URL). Restart a service only if it keeps failing after Keycloak is confirmed healthy.
5. Do not widen security (for example permitting unauthenticated access) as a workaround.

## Eureka unavailable

### Symptom

- Requests through the gateway to notification, user, or audit routes fail (typically a 503 from the
  load balancer saying no instance is available) once the gateway no longer has a usable registry.
- New or restarted service instances do not appear in the registry.
- Direct calls to a service port (for example `:8082`) keep working.

### Detection

| Signal | Where |
| --- | --- |
| Eureka health | `GET http://localhost:8761/actuator/health` (public); dashboard `http://localhost:8761` requires HTTP Basic (`EUREKA_SECURITY_USER` / `EUREKA_SECURITY_PASSWORD`) |
| Registered instances | Eureka dashboard "Instances currently registered with Eureka"; expect `AEGIS-API-GATEWAY`, `AEGIS-NOTIFICATION-SERVICE`, `AEGIS-AUDIT-SERVICE`, `AEGIS-USER-SERVICE`, `AEGIS-CONFIG-SERVER` |
| Container | `docker compose -f docker-compose.full.yml ps aegis-eureka-server` (healthcheck: `wget http://localhost:8761/actuator/health`) |
| Client logs | Registration and heartbeat failures in each service's log (framework messages from the Eureka client) |

### Diagnosis

1. Is the server up? If yes, check the credentials: a mismatch between the server's
   `EUREKA_SECURITY_*` and the `user:password@` embedded in each client's `defaultZone` causes
   401 on registration.
2. Is `EUREKA_HOST` correct for the client (`localhost` by default, `aegis-eureka-server` in
   compose)?
3. After a Eureka restart the registry is empty until clients re-register on their next heartbeat
   (framework default interval; not overridden in this repository). Short-lived 503s from the
   gateway in that window are expected.
4. Eureka self-preservation is disabled, so instances that miss renewals are evicted quickly
   (5 s eviction timer); a network blip can drop healthy instances from the registry until they
   re-register.

### Remediation

1. Restart `aegis-eureka-server`, then wait for clients to re-register and confirm the instances
   above are listed. Restarting clients is only needed if they do not re-register.
2. Fix credential or host mismatches and restart the affected client.
3. Workaround while the registry is unavailable: call `aegis-notification-service` directly on
   port 8082 with a valid token. This is also the only route for `retry` and `cancel`, which the
   gateway does not expose.
4. Startup order for a cold start: Eureka, then Config Server, then the other services (the compose
   file enforces this with health checks).

## Config Server unavailable

### Symptom

- No runtime symptom for running services; nothing polls Config Server after startup.
- A service restarted during the outage starts with its **local** `application.yml` and ignores
  centrally managed overrides, which can silently change behavior (for example rate limits or
  CORS origins on the gateway).
- Config Server itself fails at startup if its Git backend is unreachable or credentials are wrong
  (`clone-on-start: true`).

### Detection

| Signal | Where |
| --- | --- |
| Health | `GET http://localhost:8888/actuator/health` (public; details only when authorized) |
| Served config | `curl -u "$CONFIG_SERVER_USER:$CONFIG_SERVER_PASSWORD" http://localhost:8888/aegis-api-gateway/default` (standard Spring Cloud Config path form `/{application}/{profile}`) |
| Registration | `AEGIS-CONFIG-SERVER` listed in Eureka |
| Container | `docker compose -f docker-compose.full.yml ps aegis-config-server` |

### Diagnosis

1. Native mode (compose): the server serves files from `docker/config-repo` mounted at `/config-repo`.
   That directory is intentionally empty, so every client uses its own `application.yml`.
2. Git mode (default `application.yml`): check `CONFIG_REPO_URI`, `CONFIG_REPO_USERNAME`,
   `CONFIG_REPO_TOKEN`, and the `main` branch. An expired token or unreachable host stops startup.
3. Check Basic auth: clients must send `SPRING_CLOUD_CONFIG_USERNAME` / `SPRING_CLOUD_CONFIG_PASSWORD`
   matching `CONFIG_SERVER_USER` / `CONFIG_SERVER_PASSWORD`.

### Remediation

1. Restore the Git backend or credentials and restart `aegis-config-server`.
2. **Then restart any gateway, audit, or user service that was (re)started while the server was down**
   so it picks up central configuration. Do not restart them first.
3. Do not restart the notification service for this reason; it does not read Config Server.
4. To change configuration in the local stack, drop `<application-name>.yml` (for example
   `aegis-api-gateway.yml`) into `docker/config-repo`, restart `aegis-config-server`, then restart
   the affected client; there is no refresh endpoint.

## Other infrastructure (brief)

| Dependency | Effect while down | Where to look |
| --- | --- | --- |
| Kafka | Submissions are still accepted (202) because the outbox is in PostgreSQL; the relay cannot publish and events wait as `UNPROCESSED`; the consumer stalls; DLQ publishes fail. Audit event publishing is best-effort (the adapter catches and swallows failures), so audit records can be missed | [runbook 04](04-outbox-backlog.md), [runbook 03](03-dlq-messages.md) |
| PostgreSQL | API writes and reads fail; relay ticks fail (`outbox_relay_tick_failed`); health reports down | Service logs, `docker compose ps postgres` |
| Redis | Used by the gateway rate limiter on `POST /api/v1/notifications`. Failure behavior is framework-defined and not documented in this repository | Gateway logs |

## Escalation

Escalate when:

- Keycloak does not recover after a restart, or the realm cannot be re-imported (blocks all
  authenticated access).
- Eureka is up and credentials match but instances do not register (indicates network or version
  problems).
- Config Server cannot reach its Git backend and the credentials are not yours to rotate.
- A service was restarted during a Config Server outage in an environment where central
  configuration is security-relevant (CORS, rate limits, scopes).

Attach: which dependency, container logs for it, the discovery or health outputs above, the list of
services restarted during the outage and the times. The repository defines no on-call route.
