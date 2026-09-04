# Keycloak from scratch for AegisNotify frontend OIDC

Step-by-step local setup of Keycloak 26 so a browser frontend can authenticate with Authorization Code + PKCE and call the API Gateway with a Bearer JWT. Values below come from this repository: [`docker-compose.yml`](docker-compose.yml), [`docker/keycloak/aegis-realm.json`](docker/keycloak/aegis-realm.json), [`docs/security/scopes.md`](docs/security/scopes.md), [`README.md`](README.md), and [`Keycloak.md`](Keycloak.md).

This guide does not describe application source under `aegis-admin-frontend`. It only lists the Keycloak and environment values a frontend must use.

Compose publishes Keycloak on **port 8088** (`8088:8080`). Do not use `8080` (API Gateway) or `8081` as the Keycloak URL unless you change the port mapping.

## 1. Prerequisites

- Docker and Docker Compose
- Optional: `curl` and `jq` to smoke-test tokens
- Work from the **repository root** so `./docker/keycloak` mounts correctly

## 2. Start Keycloak

```bash
docker compose up -d keycloak
```

What Compose starts:

| Setting | Value |
| --- | --- |
| Image | `quay.io/keycloak/keycloak:26.0` |
| Container name | `aegis-keycloak` |
| Command | `start-dev --import-realm` |
| Admin console | `http://localhost:8088` |
| Bootstrap admin | `admin` / `admin` (override with `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD`) |
| Import volume | `./docker/keycloak` → `/opt/keycloak/data/import` (read-only) |

Wait until the process is up, then confirm the realm:

```bash
curl -s http://localhost:8088/realms/aegis/.well-known/openid-configuration
```

Open the Admin Console at `http://localhost:8088` and sign in as `admin` / `admin`. Those credentials are for local development only.

## 3. Realm: import (recommended) or create by hand

### 3.1 Import path (already done by Compose)

`--import-realm` loads [`docker/keycloak/aegis-realm.json`](docker/keycloak/aegis-realm.json). After a successful start you should already have:

- Realm **`aegis`**, enabled, `sslRequired` = `none` (local HTTP only)
- Client scopes: `notification:write`, `notification:read`, `audit:read`, `user:read`, `user:admin`
- Public client **`aegis-dev-cli`** (Direct Access Grants / password grant)
- Confidential client **`aegis-user-service`** (service account for the Admin API)
- User **`aegis-dev`** (see section 7)

Skip to section 4 unless you need a realm built only in the console.

### 3.2 Manual realm (no import)

1. In the Admin Console, open the realm dropdown (top left) and choose **Create realm**.
2. Set **Realm name** to `aegis`, leave it **Enabled**, and create it.
3. Open **Realm settings** → **General**. For local HTTP set **Require SSL** to **None**. Do not use this in production.
4. Open **Client scopes** → **Create client scope** and create each of the five scopes below. Use **Protocol** `openid-connect`. After save, open the scope → **Settings** (or attributes) and set **Include in token scope** (`include.in.token.scope`) to **true**. Do not change the names; the gateway and services match these literals exactly ([`docs/security/scopes.md`](docs/security/scopes.md)).

| Scope | Purpose |
| --- | --- |
| `notification:write` | `POST /api/v1/notifications` |
| `notification:read` | `GET /api/v1/notifications/{id}/status` |
| `audit:read` | `/api/v1/audit/**` |
| `user:read` | User list/query (assign only if the UI calls those APIs) |
| `user:admin` | User create/update/disable/password (assign only if the UI calls those APIs) |

## 4. Clients that already exist in the imported realm

Do **not** point a browser app at these as-is.

**`aegis-dev-cli`**

- Public client used by README password-grant `curl`
- Direct Access Grants enabled; Standard flow disabled in the realm JSON
- Suitable for CLI/backend smoke tests, not for Authorization Code in the browser unless you add redirect URIs and Standard flow yourself

**`aegis-user-service`**

- Confidential client with a local secret `local-dev-only-secret`
- Service account roles `view-users` and `manage-users` on `realm-management`
- Used by `aegis-user-service` via `KEYCLOAK_ADMIN_CLIENT_ID` / `KEYCLOAK_ADMIN_CLIENT_SECRET`
- Never put this secret in frontend JavaScript

If you created the realm by hand, recreate `aegis-dev-cli` only if you need the password-grant smoke test in section 9.

## 5. Create the frontend OIDC client

The SPA should use a dedicated public client. In Keycloak 26:

1. Select realm **`aegis`**.
2. **Clients** → **Create client**.
3. **General settings**
   - **Client type**: OpenID Connect
   - **Client ID**: `aegis-admin-frontend`
   - **Name**: `AegisNotify Admin Frontend` (optional display name)
4. **Capability config**
   - **Client authentication**: Off (public client; no secret)
   - **Standard flow**: On (Authorization Code)
   - **Direct access grants**: Off
   - **Implicit flow**: Off
5. **Login settings**
   - **Valid redirect URIs**: `http://localhost:4200/*`
   - **Valid post logout redirect URIs**: `http://localhost:4200/*`
   - **Web origins**: `http://localhost:4200` (CORS for the token endpoint; do not use `*` in production)
6. Save, then open the client **Advanced** tab.
7. Set **Proof Key for Code Exchange Code Challenge Method** (PKCE) to **S256**. Blank allows PKCE but does not require it; S256 is what a public SPA should enforce.

Recommended local redirect pattern is `http://localhost:4200/*`. The exact callback is often `http://localhost:4200/` or the router path the app uses. In production use exact HTTPS URIs, not wildcards.

## 6. Attach scopes to the frontend client

On client `aegis-admin-frontend` → **Client scopes**:

Add as **Default** client scopes (the access token `scope` claim must contain these strings):

- `notification:read`
- `notification:write`
- `audit:read`

Add `user:read` and `user:admin` only if this UI will call user-administration APIs. Those scopes are non-hierarchical: `user:admin` does not grant `user:read`.

Keep custom scopes as OpenID Connect client scopes with `include.in.token.scope=true`, matching [`docs/security/scopes.md`](docs/security/scopes.md).

## 7. Development user

If the realm was imported, this user already exists:

```text
Username: aegis-dev
Password:  dev123
Email:     aegis-dev@example.local
Enabled:   yes
Email verified: yes
```

To create it manually: **Users** → **Create new user** → username `aegis-dev`, email as above, **Email verified** on, **Enabled** on → **Credentials** → set password `dev123`, **Temporary** off. Local development only.

## 8. Values the frontend must use

Issuer (OIDC authority):

```text
http://localhost:8088/realms/aegis
```

| Variable / setting | Local value |
| --- | --- |
| `KEYCLOAK_URL` | `http://localhost:8088` |
| `KEYCLOAK_REALM` | `aegis` |
| `KEYCLOAK_CLIENT_ID` | `aegis-admin-frontend` |
| `API_BASE_URL` | `http://localhost:8080` |

```dotenv
API_BASE_URL=http://localhost:8080
KEYCLOAK_URL=http://localhost:8088
KEYCLOAK_REALM=aegis
KEYCLOAK_CLIENT_ID=aegis-admin-frontend
```

Related endpoints:

| Endpoint | URL |
| --- | --- |
| Well-known | `http://localhost:8088/realms/aegis/.well-known/openid-configuration` |
| Token | `http://localhost:8088/realms/aegis/protocol/openid-connect/token` |
| JWKS | `http://localhost:8088/realms/aegis/protocol/openid-connect/certs` |

Application behaviour:

- Use **Authorization Code + PKCE (S256)**. Redirect the user to Keycloak login; do not embed password grant in the browser.
- Send `Authorization: Bearer <access_token>` to the API Gateway at `http://localhost:8080`.
- Logout via Keycloak, then return to a registered post-logout URI.
- Do not call the Keycloak Admin REST API from the browser. User administration goes through backend services that use `aegis-user-service`.

`KEYCLOAK_CLIENT_ID=aegis-dev-cli` is only valid for the existing CLI client. For a SPA, prefer `aegis-admin-frontend` as configured above.

## 9. Verify tokens (backend smoke test, not the SPA)

Password grant against `aegis-dev-cli` (Standard flow is off on that client; this is CLI-only):

```bash
ACCESS_TOKEN=$(curl -s -X POST http://localhost:8088/realms/aegis/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=aegis-dev-cli" \
  -d "username=aegis-dev" \
  -d "password=dev123" \
  -d "scope=notification:write notification:read audit:read user:read user:admin" \
  | jq -r .access_token)
```

```bash
curl -i http://localhost:8080/api/v1/notifications \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

The second command needs the gateway running. For the Angular (or other SPA) app, confirm login through the browser redirect to Keycloak with PKCE, not this curl.

## 10. Reimport and production

Clean reimport (destroys the Keycloak Compose volume and **overwrites** console-only changes):

```bash
docker compose down -v
docker compose up -d keycloak
```

If you rotate `aegis-user-service`’s secret, update `KEYCLOAK_ADMIN_CLIENT_SECRET` on the user service and [`docker/keycloak/aegis-realm.json`](docker/keycloak/aegis-realm.json) if the value must survive reimport.

Production: do not use `start-dev`, HTTP, `sslRequired=none`, `admin`/`admin`, `dev123`, or `local-dev-only-secret`. Use HTTPS, environment-specific clients, exact redirect URIs, secrets outside the repo, and a JWKS URI that matches the production realm.

## Project sources

- [`docker-compose.yml`](docker-compose.yml)
- [`docker/keycloak/aegis-realm.json`](docker/keycloak/aegis-realm.json)
- [`README.md`](README.md)
- [`docs/security/scopes.md`](docs/security/scopes.md)
- [`Keycloak.md`](Keycloak.md)
- [`aegis-user-service/src/main/resources/application.yml`](aegis-user-service/src/main/resources/application.yml)
