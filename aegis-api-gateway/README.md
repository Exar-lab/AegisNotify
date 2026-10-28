# Aegis API Gateway

Reactive entry point for the AegisNotify platform, built on Spring Cloud Gateway (WebFlux).

## Overview

Responsibility:

- Validate bearer JWTs (OAuth2 resource server).
- Enforce per-route OAuth2 scopes.
- Route requests to services discovered through Eureka (`lb://`).
- Relay the incoming token to downstream services (default filter `TokenRelay`).
- Apply CORS policy.

## Routes

Routes defined in `application.yml`:

| Route id | Predicate | Target |
|----------|-----------|--------|
| `notification-submit` | `POST /api/v1/notifications` | `lb://aegis-notification-service` |
| `notification-status` | `GET /api/v1/notifications/*/status` | `lb://aegis-notification-service` |

Audit (8083) and user (8084) endpoints are not proxied; call those services directly. See [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md).

## Scope rules

`RouteScopeRules` is evaluated first-match-wins, before the catch-all `/api/v1/**` (authenticated). Full contract: [docs/security/scopes.md](../docs/security/scopes.md).

| Method and path | Required scope |
|-----------------|----------------|
| `POST /api/v1/notifications` | `notification:write` |
| `GET /api/v1/notifications`, `GET /api/v1/notifications/*/status` | `notification:read` |
| `PATCH /api/v1/notifications/*/cancel` | `notification:write` |
| `POST /api/v1/notifications/*/retry` | `notification:write` |
| `/api/v1/audit/**` | `audit:read` |
| `GET /api/v1/users/**` | `user:read` |
| `POST /api/v1/users`, `PUT /api/v1/users/**`, `PATCH /api/v1/users/**` | `user:admin` |

## Configuration

| Property | Env Variable | Default | Description |
|----------|--------------|---------|-------------|
| `server.port` | — | 8080 | HTTP port |
| `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | `JWKS_URI` | Keycloak `localhost:8088`, realm `aegis` | JWT signing keys |
| `spring.cloud.gateway.globalcors` allowed origins | `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | CORS origins; methods GET, POST, PUT, DELETE, OPTIONS |
| `spring.config.import` | — | `optional:configserver:http://localhost:8888` | Config Server, no credentials configured |
| `eureka.client.register-with-eureka` / `fetch-registry` | — | `true` / `true` | Uses the default Eureka URL |

## Security

- Reactive JWT resource server; CSRF is disabled.
- Public: `/actuator/health`, `/actuator/info`. `/actuator/prometheus` is exposed but requires authentication.
- Every other request requires authentication; `/api/v1/**` requires the scope listed above.
- Authorization failures use `ScopeAwareAccessDeniedHandler`.
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
3. `aegis-notification-service` (8082), the only routed target.
4. Optionally Config Server (8888); the import is optional.

## Run

```bash
# From project root
./mvnw spring-boot:run -pl aegis-api-gateway
```

## Known limitations

- No rate limiting is configured in this checkout.
- Only the two notification routes above are defined; scope rules for audit and user paths exist but have no matching route.
- The Config Server import carries no credentials, while the Config Server requires HTTP Basic.

## Dependencies

- Spring Cloud Gateway (WebFlux)
- Spring Security OAuth2 Resource Server, OAuth2 Client (TokenRelay)
- Spring Cloud Netflix Eureka Client and Config client
- Spring Boot Starter Actuator
