# Aegis API Gateway

Reactive entry point for the AegisNotify platform, built on Spring Cloud Gateway (WebFlux).

## Overview

Responsibility:

- Validate bearer JWTs (OAuth2 resource server).
- Enforce per-route OAuth2 scopes.
- Route requests to services discovered through Eureka (`lb://`).
- Rate-limit notification submissions per caller (Redis-backed).
- Apply CORS policy.

The gateway does not use a `TokenRelay` filter. It was removed because the gateway authenticates callers as a resource server (`JwtAuthenticationToken`), for which `TokenRelay` is a no-op (see the comment in `application.yml` and `TokenRelayPropagationTest`). The caller's `Authorization` header reaches downstream services through normal proxy header forwarding.

## Routes

Routes defined in `application.yml`:

| Route id | Predicate | Target |
|----------|-----------|--------|
| `notification-submit` | `POST /api/v1/notifications` (rate limited) | `lb://aegis-notification-service` |
| `notification-status` | `GET /api/v1/notifications/*/status` | `lb://aegis-notification-service` |
| `notification-cancel` | `PATCH /api/v1/notifications/*/cancel` | `lb://aegis-notification-service` |
| `notification-retry` | `POST /api/v1/notifications/*/retry` | `lb://aegis-notification-service` |
| `notification-list` | `GET /api/v1/notifications` | `lb://aegis-notification-service` |
| `dashboard-summary` | `GET /api/v1/dashboard/**` | `lb://aegis-notification-service` |
| `metrics-summary` | `GET /api/v1/metrics/**` | `lb://aegis-notification-service` |
| `templates-api` | `/api/v1/templates/**` (any method) | `lb://aegis-notification-service` |
| `users-api` | `/api/v1/users/**` (any method) | `lb://aegis-user-service` |
| `audit-read` | `GET /api/v1/audit/**` | `lb://aegis-audit-service` |

Notification, audit and user endpoints are proxied. See also [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md).

## Scope rules

`RouteScopeRules` is evaluated first-match-wins, before the catch-all `/api/v1/**` (authenticated). Full contract: [docs/security/scopes.md](../docs/security/scopes.md).

| Method and path | Required scope |
|-----------------|----------------|
| `POST /api/v1/notifications` | `notification:write` |
| `GET /api/v1/notifications`, `GET /api/v1/notifications/*/status` | `notification:read` |
| `PATCH /api/v1/notifications/*/cancel`, `POST /api/v1/notifications/*/retry` | `notification:write` |
| `GET /api/v1/dashboard/**`, `GET /api/v1/metrics/**` | `notification:read` |
| `GET /api/v1/templates`, `GET /api/v1/templates/*` | `notification:read` |
| `POST /api/v1/templates`, `POST /api/v1/templates/render`, `PUT /api/v1/templates/*`, `PATCH /api/v1/templates/*/status` | `notification:write` |
| `/api/v1/audit/**` (any method) | `audit:read` |
| `GET /api/v1/users/**` | `user:read` |
| `POST /api/v1/users`, `PUT /api/v1/users/**`, `PATCH /api/v1/users/**` | `user:admin` |

Any other `/api/v1/**` request only needs a valid JWT.

## Rate limiting

Only `notification-submit` (`POST /api/v1/notifications`) uses the `RequestRateLimiter` filter (Redis token bucket).

- Bucket key (`userKeyResolver`): JWT `sub`, falling back to `jti` when `sub` is absent. If no key can be resolved, the filter rejects the request (`deny-empty-key`, status 403).
- Defaults: replenish rate 100 tokens/s, 60 tokens per request (about 100 requests per minute sustained), burst capacity 1200 (20-request burst). Override with `RATE_LIMIT_REPLENISH_RATE`, `RATE_LIMIT_BURST_CAPACITY`, `RATE_LIMIT_REQUESTED_TOKENS`.
- Redis outage: the limiter degrades open (traffic is admitted), per the `RateLimiterConfig` javadoc. Lettuce timeouts are short (`REDIS_TIMEOUT` 250ms, `REDIS_CONNECT_TIMEOUT` 500ms) and the Redis health contributor surfaces the state at `/actuator/health`.

## Configuration

| Property | Env Variable | Default | Description |
|----------|--------------|---------|-------------|
| `server.port` | — | 8080 | HTTP port |
| `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | `JWKS_URI` | Keycloak `localhost:8088`, realm `aegis` | JWT signing keys |
| CORS allowed origin (`corsConfigurationSource` in `SecurityConfig`) | `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Single origin; methods GET, POST, PUT, PATCH, DELETE, OPTIONS; all headers |
| `spring.data.redis.host` / `port` | `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Rate limiter store |
| `spring.data.redis.timeout` / `connect-timeout` | `REDIS_TIMEOUT` / `REDIS_CONNECT_TIMEOUT` | `250ms` / `500ms` | Redis timeouts |
| `spring.config.import` | — | `optional:configserver:` | Config Server (Spring default URI), optional |
| `eureka.client.service-url.defaultZone` | `EUREKA_HOST`, `EUREKA_SECURITY_USER`, `EUREKA_SECURITY_PASSWORD` | host `localhost`; no default for user and password | Eureka at port 8761 with basic credentials in the URL |

CORS is enforced by Spring Security (`SecurityConfig`), not `spring.cloud.gateway.globalcors`.

## Security

- Reactive JWT resource server; CSRF is disabled.
- Public: `OPTIONS` preflight requests, `/actuator/health`, `/actuator/info`. `/actuator/prometheus` is exposed but requires authentication.
- Every other request requires authentication; `/api/v1/**` requires the scope listed above.
- Authorization failures use `ScopeAwareAccessDeniedHandler`, which returns a 403 JSON body naming the required scope (`insufficient_scope`).
- Downstream services validate the JWT again; the gateway is not the only enforcement point.

## API documentation routes

Only with the Spring profile `local`, the gateway proxies each service's docs (public, no JWT) with `StripPrefix=2`:

- `/docs/notification/**` -> `aegis-notification-service` (e.g. `/docs/notification/v3/api-docs`, `/docs/notification/swagger-ui/index.html`)
- `/docs/user/**` -> `aegis-user-service`
- `/docs/audit/**` -> `aegis-audit-service`

The services must also run with the `local` profile (docs are disabled otherwise). Without `local` the routes do not exist and `/docs/**` returns 401. The routes are defined in `ApiDocsConfig` (not YAML) so they add to, rather than replace, the base route list.

## Startup order and dependencies

Start last, after the services it routes to:

1. Eureka Server (8761); the gateway registers and fetches the registry.
2. Keycloak (8088, JWKS endpoint).
3. Redis (rate limiting; the gateway degrades open if it is down).
4. The routed services: `aegis-notification-service`, `aegis-user-service`, `aegis-audit-service`.
5. Optionally Config Server (8888); the import is optional.

## Run

```bash
# From project root
./mvnw spring-boot:run -pl aegis-api-gateway
```

## Known limitations

- Rate limiting applies only to `POST /api/v1/notifications`.
- The Config Server import carries no credentials, while the Config Server requires HTTP Basic.

## Dependencies

- Spring Cloud Gateway (WebFlux)
- Spring Security OAuth2 Resource Server (the OAuth2 Client starter is also in the `pom.xml`, but `TokenRelay` is not used)
- Spring Data Redis Reactive (rate limiter)
- Spring Cloud Netflix Eureka Client and Config client
- Spring Boot Starter Actuator
