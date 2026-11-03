# ADR-0005: Keycloak JWT scopes enforced at the gateway and the services

- **Status:** Accepted
- **Date:** 2026-06-25 (gateway JWT validation, `74c1339`); 2026-08-12 (per-route scopes, PR #77,
  `d5c6ec0`, `be84e9d`; Keycloak realm, `c8ac252`)
- **Evidence:** `74c1339`, `2fa997c`, `be84e9d`, `d5c6ec0`, `c8ac252`, `RouteScopeRules`,
  `docs/security/scopes.md`

## Context

All endpoints sit behind an API gateway, and several services expose different capabilities
(submitting notifications, reading status, reading audit data, administering users). Issue #74
introduced scope-based authorization. The project documentation names Keycloak as the identity
provider and the scope `notification:write` as the original requirement (`CLAUDE.md`).

## Decision

Authenticate with Keycloak-issued JWTs and authorize with OAuth2 scopes, checked in two places.

- **Gateway.** `aegis-api-gateway` validates the JWT as an OAuth2 resource server and relays the
  token downstream (`74c1339`). `RouteScopeRules` maps each route pattern and HTTP method to a
  required scope (design decision A1), and the gateway returns a 403 that names the missing scope.
- **Services.** `aegis-notification-service` and `aegis-audit-service` independently validate the
  JWT against the Keycloak JWKS URI and re-check scopes (defense in depth, per
  `docs/security/scopes.md`).
- **Scopes.** `notification:write`, `notification:read`, `audit:read`, `user:read`, `user:admin`.
  Scopes are non-hierarchical: `user:admin` does not imply `user:read`. Unmapped routes remain
  `.authenticated()` (deny-by-default), and malformed or expired tokens return 401 rather than 403.
- **Local development.** `docker-compose.yml` runs Keycloak 26 with an imported `aegis` realm
  (`docker/keycloak/aegis-realm.json`) that declares all five scopes (`c8ac252`).

## Alternatives considered

`docs/security/scopes.md` records two design choices with named alternatives implied by the text:
a hierarchical scope model (rejected: scopes are explicitly non-hierarchical) and enforcement only
at the gateway (not chosen: services re-check). The repository does not record a comparison with
an identity provider other than Keycloak.

## Consequences

### Positive

- A request is checked at the edge and again in the owning service, so bypassing the gateway does
  not bypass authorization.
- Each capability has a named scope, and the table in `docs/security/scopes.md` is the contract.
  Canary tests (`SecurityScopesTest`) assert the literals.
- Only the gateway reveals the required scope in its 403 body; downstream bodies stay opaque.

### Negative / trade-offs

- The scope literals live in code, in the realm export, and in `scopes.md`; drift between them is a
  bug the tests only partly catch (`scopes.md` says so).
- Gateway and services must stay in sync on every new route.

## Related

- Issue #74, PRs #77 and #88; `docs/security/scopes.md`.
- Admin frontend scope grants: `49a17da` (2026-10-30).
