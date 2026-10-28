# Aegis API Gateway

Reactive entry point for the AegisNotify platform, built on Spring Cloud Gateway.

## Responsibility

- Routes `/api/v1/**` requests to services discovered through Eureka (`lb://` URIs) on port 8080.
- Validates JWTs and enforces a per-route OAuth2 scope before proxying.
- Applies CORS and a Redis-backed rate limit on `POST /api/v1/notifications`.

| Route | Target service |
| --- | --- |
| `POST/GET /api/v1/notifications`, `GET /api/v1/notifications/*/status` | `aegis-notification-service` |
| `GET /api/v1/dashboard/**`, `GET /api/v1/metrics/**` | `aegis-notification-service` |
| `/api/v1/users/**` | `aegis-user-service` |
| `GET /api/v1/audit/**` | `aegis-audit-service` |

## Security

- JWT resource server (`JWKS_URI`, default: the local Keycloak realm). CSRF is disabled.
- Required scopes are defined in `RouteScopeRules` (`notification:write`, `notification:read`, `audit:read`, `user:read`, `user:admin`); see [`docs/security/scopes.md`](../docs/security/scopes.md). Scope rules are registered before the generic `/api/v1/**` authenticated rule, and the order matters.
- Public: `OPTIONS` preflight requests, `/actuator/health`, and `/actuator/info`. Everything else, including `/actuator/prometheus`, requires authentication.
- CORS allows a single origin from `CORS_ALLOWED_ORIGINS` (code default `http://localhost:4200`) with methods GET, POST, PUT, DELETE, and OPTIONS.
- Rate limiting is keyed by JWT subject (falling back to `jti`). If Redis is unreachable, the limiter degrades open: traffic is allowed and the failure shows in `/actuator/health`. Tuning variables: `RATE_LIMIT_REPLENISH_RATE`, `RATE_LIMIT_BURST_CAPACITY`, `RATE_LIMIT_REQUESTED_TOKENS`.
- The caller's `Authorization` header is forwarded unchanged to downstream services, which validate it themselves.

## Startup order and dependencies

| Dependency | Default | Notes |
| --- | --- | --- |
| Eureka Server | `http://localhost:8761/eureka/` | Registers and fetches the registry |
| Keycloak (JWKS) | `JWKS_URI` | Token signature validation |
| Redis | `REDIS_HOST` / `REDIS_PORT` = `localhost:6379` | Rate limiter; not started by this repository's compose files |
| Config Server | `optional:configserver:http://localhost:8888` | Optional; local `application.yml` is the fallback |
| Downstream services | via Eureka | Notification, audit, and user services |

Start the gateway last, after Eureka and the services it routes to. This module's `application.yml` does not define a Eureka `service-url` or Config Server credentials; the shared `config-repo/application.yml` provides the Eureka URL with credentials when the Config Server is in use.

```bash
./mvnw -pl aegis-api-gateway spring-boot:run
```

See the [root README](../README.md) for the full platform setup.
