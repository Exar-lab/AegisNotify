# Keycloak Realm Setup

How AegisNotify uses Keycloak: the `aegis` realm, its clients, scopes and roles, and how
a token travels from the caller through the gateway to the services (issue #199).

> Everything here describes the **local development** realm shipped in
> [`docker/keycloak/aegis-realm.json`](../../docker/keycloak/aegis-realm.json). It is not a
> production configuration (HTTP only, `start-dev`, fixed dev credentials). Secrets and
> passwords are deliberately **not** repeated in this file: read them from the realm export
> and [`.env.example`](../../.env.example), and replace them in any shared environment.

The scope contract itself (which scope guards which endpoint) lives in
[`scopes.md`](scopes.md). The design rationale is in
[ADR-0005](../adr/0005-keycloak-jwt-scopes-at-the-gateway.md).

## 1. Reproduce the realm locally

Prerequisites: Docker and Docker Compose.

```bash
docker compose up -d keycloak
```

What this does (see [`docker-compose.yml`](../../docker-compose.yml)):

| Item | Value |
|---|---|
| Image | `quay.io/keycloak/keycloak:26.0` |
| Command | `start-dev --import-realm` |
| Host port | `8088` (container `8080`) |
| Realm import | `./docker/keycloak` mounted read-only at `/opt/keycloak/data/import` |
| Login theme | `./docker/keycloak/themes/aegis` mounted read-only at `/opt/keycloak/themes/aegis` |
| Admin console login | `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD` (defaults in `docker-compose.yml`, overridable via `.env`) |

Verify the realm is live:

```bash
curl -s http://localhost:8088/realms/aegis/.well-known/openid-configuration
```

The full-stack file `docker-compose.full.yml` pulls the same service in with `include:`; do not
also pass `-f docker-compose.yml`. See [`../deployment.md`](../deployment.md).

### Re-importing after editing the realm file

`--import-realm` only imports a realm that does not exist yet. Keycloak does not overwrite an
existing realm on restart, so edits to `aegis-realm.json` are not picked up by a plain
restart. These compose files declare no Keycloak volume, so recreating the container with
`-v` resets it to the file:

```bash
docker compose down -v && docker compose up -d keycloak
```

This discards any change made through the admin console. To keep a console change, mirror it in
`aegis-realm.json`.

### Realm settings

| Setting | Value |
|---|---|
| Realm | `aegis` |
| `sslRequired` | `none` (local only) |
| `registrationAllowed` | `false` |
| `loginTheme` | `aegis` (see [`login-theme.md`](login-theme.md)) |

## 2. Clients

| Client ID | Type | Flows enabled | Default client scopes | Purpose |
|---|---|---|---|---|
| `aegis-dev-cli` | Public | Direct access grants (password) | all five | Local JWT retrieval with `curl` (README, "7. Get a token and call the API"). Not for production. |
| `aegis-admin-frontend` | Public | Standard flow (authorization code) | all five | Angular admin UI login. Redirect URIs and web origins are `http://localhost:4200`. |
| `aegis-user-service` | Confidential | Service account only | none | `aegis-user-service` calls the Keycloak Admin REST API with the client-credentials grant. |

Notes:

- `aegis-admin-frontend` is described in the realm as using authorization code flow with PKCE,
  but the export carries no PKCE attribute and the frontend does not set `pkceMethod`
  explicitly. Treat PKCE as **not enforced by the realm**; enforce it (client setting
  *Proof Key for Code Exchange Code Challenge Method* = `S256`) before relying on it.
- `aegis-user-service` has a client secret. Its local value is in the realm export; the service
  reads it from `KEYCLOAK_ADMIN_CLIENT_SECRET` (`docker-compose.full.yml` defaults it for local
  runs). Rotate it for any shared environment and keep it out of version control.
- No client in the export defines custom protocol mappers; scopes reach the token through the
  standard `scope` claim.

## 3. Scopes

The realm declares five client scopes, as `clientScopes` with
`include.in.token.scope: "true"` and `display.on.consent.screen: "false"`:

| Scope | Guards (summary) |
|---|---|
| `notification:write` | Submit, cancel, retry notifications; create/update templates |
| `notification:read` | Status, list, dashboard, metrics, read templates |
| `audit:read` | `/api/v1/audit/**` |
| `user:read` | List/read users and roles |
| `user:admin` | Create/update users, roles, status, password reset |

Endpoint-level detail: [`scopes.md`](scopes.md). Because the literals are duplicated in
`SecurityScopesTest` in each service, renaming a scope requires changing the realm export, the
code and `scopes.md` together.

Both public clients list all five scopes as *default* client scopes, so every token they obtain
carries all five. To issue narrower tokens, move scopes to *optional* and request them
explicitly with the `scope` parameter.

## 4. Roles

The realm export declares **no custom realm roles and no custom client roles**. Authorization in
AegisNotify is by scope, not by role. Roles appear in two places only:

| Role | Where | Meaning |
|---|---|---|
| `view-users`, `manage-users` (client `realm-management`) | Service account of `aegis-user-service` | Lets the user service read and mutate users through the Admin REST API. |
| Realm roles (Keycloak built-ins such as `default-roles-aegis`, plus any you create) | Managed through `aegis-user-service` | `GET /api/v1/users/roles` lists realm roles; `PUT /api/v1/users/{id}/roles` assigns them. They are data managed by the admin UI, not an authorization input to the gateway. |

The user service also calls `GET /admin/realms/{realm}/roles`. Keycloak normally requires
`view-realm` (or an equivalent) for that call, which the service account in the export does not
have. If listing roles returns 403 against a real Keycloak, add the `view-realm` role from
`realm-management` to the service account.

## 5. Token flow

```text
Caller ──(1) password / auth-code grant──▶ Keycloak (aegis realm)
Caller ◀─(2) access token (JWT, `scope` claim)────────────────┘

Caller ──(3) Authorization: Bearer <JWT>──▶ API Gateway (:8080)
         gateway: verify signature + expiry via JWKS,
                  RouteScopeRules → required scope
                  missing scope → 403 naming the scope; bad/expired token → 401
Gateway ──(4) same Authorization header──▶ downstream service
         service: verify the JWT again against the JWKS, re-check the scope
```

- **Validation is signature and expiry only.** Every service sets `jwk-set-uri`
  (default `http://localhost:8088/realms/aegis/protocol/openid-connect/certs`, override with
  `JWKS_URI`); none configures `issuer-uri`, so the `iss` claim is **not** checked.
- **No token relay filter.** The gateway authenticates callers as an OAuth2 resource server and
  forwards the caller's own `Authorization` header unchanged (see the comment in
  `aegis-api-gateway/src/main/resources/application.yml`).
- **Defense in depth.** The gateway and the downstream service enforce the same scope. Only the
  gateway names the missing scope in its 403 body.
- **Actuator** health, info and prometheus endpoints are public.
- **Service-to-Keycloak calls** are separate: `aegis-user-service` fetches its own token from
  `/realms/{realm}/protocol/openid-connect/token` (client credentials) and calls
  `/admin/realms/{realm}/users...`.

Obtain a local token with the command in the root README ("7. Get a token and call the API") using the
`KEYCLOAK_DEV_*` variables from `.env.example`, then inspect the `scope` claim of the result to
confirm all five scopes are present.

## 6. Configuration reference

| Variable | Default | Used by |
|---|---|---|
| `JWKS_URI` | `http://localhost:8088/realms/aegis/protocol/openid-connect/certs` | Gateway, notification, audit, user services |
| `KEYCLOAK_BASE_URL` | `http://localhost:8088` | User service |
| `KEYCLOAK_REALM` | `aegis` | User service |
| `KEYCLOAK_ADMIN_CLIENT_ID` | `aegis-user-service` | User service |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | none (set it) | User service |
| `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD` | see `docker-compose.yml` | Keycloak admin console |

## 7. Known gaps

- `DELETE /api/v1/users/**` is authorized with `user:read` in the user service `SecurityConfig`
  on purpose: no delete route exists, so an otherwise-authorized DELETE reaches Spring MVC and
  returns 405 instead of a misleading 403. If a real delete endpoint is added, it needs its own
  scope decision (likely `user:admin`).
- `docs/security/screenshots/README.md` lists placeholder PNGs that have not been captured.
- Before production: HTTPS, rotate every dev credential, restrict redirect URIs, add
  `issuer-uri`, enforce PKCE, and replace `start-dev` with a production Keycloak setup.
