# OAuth2 Scopes

Human-readable contract for the OAuth2 scopes AegisNotify enforces (issue #74).

Every scope literal in this table is duplicated as a hardcoded assertion in a
`SecurityScopesTest` canary in each service that enforces it (design decision
A4). If a scope literal changes in code without updating this file, the
canary test's intent is defeated — treat any drift between this table and a
service's `infrastructure.security.SecurityScopes` (or, for the gateway,
`com.aegisnotify.gateway.security.SecurityScopes`) as a bug.

Once `docker/keycloak/aegis-realm.json` ships (issue #74 slice 2), it MUST
declare these same five scopes, byte-for-byte, as `clientScopes` with
`attributes."include.in.token.scope": "true"`.

## Scope Table

| Scope | Enforced by | Endpoint(s) | Status |
|---|---|---|---|
| `notification:write` | `aegis-notification-service`, `aegis-api-gateway` | `POST /api/v1/notifications` | Active |
| `notification:read` | `aegis-notification-service`, `aegis-api-gateway` | `GET /api/v1/notifications/{id}/status` | Active |
| `audit:read` | `aegis-audit-service`, `aegis-api-gateway` | `/api/v1/audit/**` | Active |
| `user:read` | `aegis-user-service` (not yet built), `aegis-api-gateway` | `GET /api/v1/users`, `GET /api/v1/users/{id}` | Forward-looking — gateway rule reserved, `aegis-user-service` ships in a later slice |
| `user:admin` | `aegis-user-service` (not yet built), `aegis-api-gateway` | `POST /api/v1/users`, `PUT /api/v1/users/{id}`, `PATCH /api/v1/users/{id}/status`, `PUT /api/v1/users/{id}/password` | Forward-looking — gateway rule reserved, `aegis-user-service` ships in a later slice |

## Rules

- **Deny-by-default.** Every non-actuator endpoint requires a named scope.
  Routes without an explicit mapping stay `.authenticated()` — never fully
  open (see `api-authorization` spec, "Deny-by-default for unmatched routes").
- **Defense-in-depth.** The gateway enforces the same scope its downstream
  service requires. A request that clears the gateway's check is re-checked
  by the downstream service.
- **Non-hierarchical.** `user:admin` does NOT implicitly grant `user:read`.
  A token with only `user:admin` is rejected on read (list/query) endpoints.
- **Gateway-only informative 403.** Only the gateway names the missing scope
  in its 403 body (`{"error","message","requiredScope","path","timestamp"}`),
  and never includes token claims, user identity, or realm internals.
  Downstream services (`aegis-notification-service`, `aegis-audit-service`,
  and eventually `aegis-user-service`) keep Spring Security's opaque default
  403 body — this is intentional (design decision A2), not an oversight.
- **Malformed/expired tokens return 401, not 403.** Scope checks only run
  after successful JWT authentication.

## Local Development

Actuator health, info, and prometheus endpoints remain public
(`permitAll()`) in every service and are not part of this scope table.
