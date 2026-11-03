# Keycloak realm, clients, and login theme

How AegisNotify uses Keycloak for authentication, and how to reproduce the local realm and customize
the login theme (issue #199).

Source of truth for everything below:

- [`docker/keycloak/aegis-realm.json`](../../docker/keycloak/aegis-realm.json): the realm import.
- [`docker-compose.yml`](../../docker-compose.yml): the Keycloak container.
- [`docker/keycloak/themes/aegis/login/`](../../docker/keycloak/themes/aegis/login): the login theme.
- [OAuth2 scopes](scopes.md): the scope contract each service enforces.

The realm file is for **local development only**. It ships a test user, a password-grant client, and a
client secret in plain text, and the container runs `start-dev` with `sslRequired: none`. Do not reuse
any of it in a shared or production environment (see [Hardening for non-local use](#hardening-for-non-local-use)).

For a step-by-step walkthrough in Spanish aimed at the Angular frontend, see
[`aegis-admin-frontend/keycloak-documentation/keycloak-setup.md`](../../aegis-admin-frontend/keycloak-documentation/keycloak-setup.md).

## Set up the realm

### Import (recommended)

From the repository root:

```bash
docker compose up -d keycloak
```

| Setting | Value |
|---|---|
| Image | `quay.io/keycloak/keycloak:26.0` |
| Container | `aegis-keycloak` |
| Command | `start-dev --import-realm` |
| Host port | `8088` (maps to `8080` in the container; the API gateway uses `8080` on the host) |
| Admin console | `http://localhost:8088`, bootstrap user `admin` / `admin` |
| Bootstrap override | `KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD` |
| Import mount | `./docker/keycloak` to `/opt/keycloak/data/import` (read-only) |
| Theme mount | `./docker/keycloak/themes/aegis` to `/opt/keycloak/themes/aegis` (read-only) |

Verify the realm was imported:

```bash
curl -s http://localhost:8088/realms/aegis/.well-known/openid-configuration | jq .issuer
```

Expected: `"http://localhost:8088/realms/aegis"`.

`--import-realm` only imports a realm that does not exist yet. After editing `aegis-realm.json`,
re-import from scratch:

```bash
docker compose down -v && docker compose up -d keycloak
```

`down -v` also discards every change made in the admin console, so export anything you want to keep first.

### Realm settings

| Setting | Value |
|---|---|
| Realm name | `aegis` |
| Enabled | `true` |
| Login theme | `aegis` |
| Self-registration | disabled (`registrationAllowed: false`) |
| SSL required | `none` (local only) |

Everything not listed uses Keycloak defaults, including token and session lifetimes.

## Clients

| Client ID | Type | Flows | Used by | Scopes granted by default |
|---|---|---|---|---|
| `aegis-admin-frontend` | Public | Authorization Code with PKCE (`standardFlowEnabled`) | Angular admin console | all five |
| `aegis-dev-cli` | Public | Direct access grants (password) only | Local `curl` token retrieval | all five |
| `aegis-user-service` | Confidential | Service account only | `aegis-user-service` calling the Keycloak Admin REST API | none |

Details:

- **`aegis-admin-frontend`** allows redirect URIs `http://localhost:4200/*`,
  `http://localhost:4200/silent-check-sso.html`, and `http://localhost:4200`, with web origins
  `http://localhost:4200` and `+`. Add your own origin to both lists if you serve the console elsewhere.
- **`aegis-dev-cli`** exists for the `curl` examples in the [README](../../README.md). It is described in
  the realm file as "Not for production use".
- **`aegis-user-service`** has no OAuth scopes. Instead, its service account (user
  `service-account-aegis-user-service`) holds the `realm-management` client roles `view-users` and
  `manage-users`, which is what lets the user service list, create, and update users. The service reads its
  secret from `KEYCLOAK_ADMIN_CLIENT_SECRET`, which has no default and must be set. The realm file
  contains a local-only secret for this client.

## Scopes

The realm defines five OpenID Connect client scopes, each with `include.in.token.scope: true` and
`display.on.consent.screen: false`:

| Client scope | Purpose |
|---|---|
| `notification:write` | Submit, cancel, and retry notifications; manage templates |
| `notification:read` | Read notifications, dashboard, metrics, and templates |
| `audit:read` | Read audit trails |
| `user:read` | Read users and realm roles |
| `user:admin` | Create and edit users, assign roles, reset passwords |

Endpoint-level detail is in [scopes.md](scopes.md). The names must match the literals in each service's
`SecurityScopes` class byte for byte; if a scope is renamed in code, rename it here and in the realm file.

Scopes are requested by the client. Both `aegis-admin-frontend` and `aegis-dev-cli` have all five as
**default** client scopes, so a token carries all five whether or not the caller asks for them.

## Roles

The realm file defines **no realm roles** and no mapping from roles or users to scopes. As imported:

- Every user who signs in through `aegis-admin-frontend` receives all five scopes.
- Authorization in the services is scope-based. None of the services read roles from the token, so a realm
  role assigned to a user does not change what that user can call.

`aegis-user-service` can list realm roles and assign them to users (`GET /api/v1/users/roles`,
`PUT /api/v1/users/{id}/roles`). A role must already exist in the realm; assigning a name that does not
exist fails with a "role not found" error. Create roles in the admin console under
**Realm roles** before assigning them.

See [Restricting access](#restricting-access) for what changing this involves.

### Seeded users

| User | Purpose | Credentials |
|---|---|---|
| `aegis-dev` | Local test user (`aegis-dev@example.local`) | `dev123`, not temporary |
| `service-account-aegis-user-service` | Service account for `aegis-user-service` | none (client credentials) |

## Token flow

```text
Browser / curl
   |  1. Authorization Code + PKCE (frontend) or password grant (dev CLI)
   v
Keycloak (:8088)  ---- access token (JWT, scope claim) ---->  caller
                                                                |
   2. Authorization: Bearer <JWT>                               v
                                                      API Gateway (:8080)
   - validates the signature against the realm JWKS
   - maps each `scope` entry to a SCOPE_<scope> authority
   - applies RouteScopeRules; 401 for a bad token, 403 for a missing scope
                                                                |
   3. forwards the same Authorization header, unchanged         v
                                            notification / audit / user service
   - validates the JWT again against the same JWKS
   - re-checks the same scope (defense in depth)
```

Points worth knowing:

- Every service, including the gateway, validates tokens with `jwk-set-uri`, defaulting to
  `http://localhost:8088/realms/aegis/protocol/openid-connect/certs`. Override it with the `JWKS_URI`
  environment variable. In `docker-compose.full.yml` it is `http://keycloak:8080/realms/aegis/...`.
- Only `jwk-set-uri` is configured, not `issuer-uri`. Services verify the signature and expiry, but do not
  check the token's `iss` claim against an expected issuer.
- The gateway does not exchange or relay tokens. The caller's `Authorization` header reaches the downstream
  service as is.
- The gateway names the missing scope in its 403 body. Downstream services return Spring Security's opaque
  403. See [scopes.md](scopes.md#rules).

### Get a token locally

```bash
ACCESS_TOKEN=$(curl -s -X POST http://localhost:8088/realms/aegis/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=aegis-dev-cli" \
  -d "username=aegis-dev" \
  -d "password=dev123" \
  -d "scope=notification:write notification:read audit:read user:read user:admin" \
  | jq -r .access_token)
```

Inspect the granted scopes:

```bash
echo "$ACCESS_TOKEN" | jq -R 'split(".")[1] | @base64d | fromjson | .scope'
```

## Restricting access

The shipped realm grants every scope to every user on purpose, to keep local development simple. The
repository does not include a least-privilege configuration, and the steps below have not been tested
against this realm; check them against the
[Keycloak client scopes documentation](https://www.keycloak.org/docs/latest/server_admin/#_client_scopes)
for your version.

The general approach to model something like a read-only operator:

1. On the client (**Clients > aegis-admin-frontend > Client scopes**), move the scopes that not everyone
   should hold from **Default** to **Optional**, so they are only issued when a client explicitly requests
   them.
2. Create realm roles (for example `notification-operator`) and assign them to the intended users.
3. Request a token as each kind of user and confirm the `scope` claim contains only what you expect.

Remember the scopes are non-hierarchical: `user:admin` does not imply `user:read`. Also note that moving a
scope to Optional only controls what the client asks for; the services still decide access purely from the
`scope` claim.

## Hardening for non-local use

Before using this realm outside a laptop:

- Replace the `aegis-user-service` client secret and keep it out of version control; supply it through
  `KEYCLOAK_ADMIN_CLIENT_SECRET`.
- Delete the `aegis-dev` user and the `aegis-dev-cli` client.
- Change the bootstrap admin credentials, and run Keycloak with `start` behind TLS instead of `start-dev`.
- Set `sslRequired` to `external` or `all`.
- Replace the localhost redirect URIs and web origins on `aegis-admin-frontend`.
- Consider setting `issuer-uri` on the services so the `iss` claim is validated.

## Login theme

The `aegis` theme restyles the Keycloak login pages. The realm selects it with `loginTheme: aegis`.

### Layout

```text
docker/keycloak/themes/aegis/login/
├── theme.properties            # parent theme and stylesheet registration
├── template.ftl                # page layout override (header, brand mark, form wrapper)
└── resources/
    └── css/login.css           # colors, typography, and component styling
```

`theme.properties`:

```properties
parent=keycloak.v2
import=common/keycloak
styles=css/login.css
```

The theme extends Keycloak's `keycloak.v2` theme, so any page or resource that the theme does not override
(forms, messages, scripts, the favicon) comes from the parent. Only the layout template and one stylesheet
are replaced.

### What each file controls

- **`template.ftl`** overrides the `registrationLayout` macro that wraps every login page. It renders the
  header with the AegisNotify wordmark and an inline SVG mark (`.aegis-brand-icon`), the language selector
  when the realm has more than one locale, the page title slot, and the dark-mode switch that toggles the
  `pf-v5-theme-dark` class from `prefers-color-scheme`.
- **`login.css`** sets the palette through CSS variables, then restyles PatternFly 5 classes
  (`pf-v5-c-login`, `pf-v5-c-button`, `pf-v5-c-form-control`, `pf-v5-c-alert`).

### Customize branding

Colors are defined once at the top of `login.css`:

```css
:root {
  --aegis-bg: #0e0f12;
  --aegis-surface: #15161a;
  --aegis-border: #26282e;
  --aegis-text: #f2f2f0;
  --aegis-text-secondary: #9a9ea5;
  --aegis-accent: #f4094c;
  --aegis-accent-hover: #ff4d80;
}
```

| To change | Edit |
|---|---|
| Colors | the `--aegis-*` variables in `login.css` |
| Font | the `@import` on the first lines of `login.css` and the `font-family` on `body#keycloak-bg`. Inter and Outfit are loaded at runtime from Google Fonts, so the login page needs internet access from the browser. For an offline or privacy-sensitive deployment, remove the `@import` and serve the fonts from `resources/` instead; the CSS falls back to system fonts meanwhile |
| Brand mark | the inline `<svg>` inside `.aegis-brand-icon` in `template.ftl`; the stroke uses `currentColor`, so it follows the CSS color |
| Product name | the `<span>AegisNotify</span>` next to the icon in `template.ftl` |
| Background glow | the `::before` and `::after` rules on `.pf-v5-c-login` in `login.css` |
| Extra stylesheet | add the file under `resources/css/` and append it to `styles=` in `theme.properties`, space-separated |

Add static assets (logo, favicon) under `resources/img/` and reference them with
`${url.resourcesPath}/img/<file>`. `template.ftl` already links `${url.resourcesPath}/img/favicon.ico`,
which falls back to the parent theme until you add one.

### Build and deploy

The theme has no build step: it is plain FreeMarker and CSS.

**Local (Docker Compose).** `docker-compose.yml` mounts the theme directory read-only, so edits to
the files on disk are visible inside the container. `start-dev` disables theme caching, so a browser
refresh picks up CSS and template changes without restarting Keycloak. If a change does not appear,
restart the container:

```bash
docker compose restart keycloak
```

**Other Keycloak installations.** Copy the theme folder to Keycloak's `themes/` directory so the result is
`<keycloak>/themes/aegis/login/...`, or mount it into the container at `/opt/keycloak/themes/aegis`:

```bash
docker run --rm -p 8088:8080 \
  -v "$PWD/docker/keycloak/themes/aegis:/opt/keycloak/themes/aegis:ro" \
  quay.io/keycloak/keycloak:26.0 start-dev
```

Outside `start-dev`, Keycloak caches themes. Restart it after a change, or start it with
`--spi-theme--cache-themes=false` and `--spi-theme--cache-templates=false` while developing.

Then select the theme: **Realm settings > Themes > Login theme > aegis** (already set when the realm is
imported from `aegis-realm.json`).

### Troubleshooting

| Symptom | Likely cause |
|---|---|
| Login page looks like stock Keycloak | The theme is not mounted at `/opt/keycloak/themes/aegis`, or the realm's **Login theme** is not `aegis`. Check `docker compose exec keycloak ls /opt/keycloak/themes`. |
| CSS changes do not show | Theme caching is on (not `start-dev`), or the browser cached `login.css`. Restart Keycloak and hard-refresh. |
| Layout breaks after a Keycloak upgrade | `template.ftl` is a copy of the parent template. When upgrading Keycloak, diff it against the new `keycloak.v2` `template.ftl` and port changes. |
| Realm changes in `aegis-realm.json` are ignored | `--import-realm` skips an existing realm. Run `docker compose down -v` first. |
