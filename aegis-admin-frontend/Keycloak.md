# Keycloak Setup & Configuration Guide for Aegis Admin Frontend

This guide provides step-by-step instructions to set up, configure, and connect a Keycloak instance from scratch for the `aegis-admin-frontend` application, ensuring seamless OIDC authentication with the AegisNotify ecosystem.

---

## Table of Contents

1. [Prerequisites & Running Keycloak](#1-prerequisites--running-keycloak)
2. [Realm Creation](#2-realm-creation)
3. [Client Scopes Setup](#3-client-scopes-setup)
4. [Creating the Frontend Client](#4-creating-the-frontend-client)
5. [User Creation and Role Assignment](#5-user-creation-and-role-assignment)
6. [Frontend Environment Configuration](#6-frontend-environment-configuration)
7. [Security Considerations & Best Practices](#7-security-considerations--best-practices)
8. [Verifying the Authentication Flow](#8-verifying-the-authentication-flow)

---

## 1. Prerequisites & Running Keycloak

You can start a local Keycloak instance using Docker or Podman.

### Using Docker Compose (Recommended)

From the project root:

```bash
docker compose up -d keycloak
```

Or run standalone container:

```bash
docker run -d --name aegis-keycloak \
  -p 8088:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:26.0 start-dev
```

- **Admin Console URL**: `http://localhost:8088` (or `http://localhost:8081` depending on your mapped port)
- **Initial Username**: `admin`
- **Initial Password**: `admin`

---

## 2. Realm Creation

1. Open the Keycloak Admin Console at `http://localhost:8088` and log in.
2. In the top-left dropdown (default `master`), click **Create Realm**.
3. Set the **Realm name** to `aegis`.
4. Ensure **Enabled** is toggled **ON**.
5. Click **Create**.

> **Note**: The realm name **must** match the backend expectation (`aegis`) configured across `aegis-api-gateway`, `aegis-notification-service`, and `aegis-audit-service`.

---

## 3. Client Scopes Setup

The AegisNotify backend enforces granular OAuth2 scopes defined in `docs/security/scopes.md`.

Navigate to **Client scopes** in the left menu and create the following scopes (Type: `OpenID Connect`):

| Scope Name | Description | Include in Token Scope |
| --- | --- | --- |
| `notification:write` | Submit notifications for delivery | **ON** |
| `notification:read` | Read notification status | **ON** |
| `audit:read` | Query audit trail records | **ON** |
| `user:read` | Query managed users | **ON** |
| `user:admin` | Manage users and permissions | **ON** |

For each scope:
1. Click **Create client scope**.
2. Enter the **Name** (e.g. `notification:write`).
3. Set **Protocol** to `OpenID Connect`.
4. Ensure **Include in token scope** is turned **ON** in the Settings tab.
5. Save.

---

## 4. Creating the Frontend Client

1. In the left navigation, go to **Clients** and click **Create client**.
2. **General Settings**:
   - **Client type**: `OpenID Connect`
   - **Client ID**: `aegis-dev-cli` (or `aegis-admin-frontend`)
   - **Name**: `Aegis Admin Frontend`
   - Click **Next**.
3. **Capability Config**:
   - **Client authentication**: **OFF** (Public client — Single Page Application)
   - **Authentication flow**:
     - Check **Standard flow** (Authorization Code with PKCE).
     - Check **Direct access grants** (optional for CLI/curl testing).
     - Ensure **Client authentication** is disabled (no client secret).
   - Click **Next**.
4. **Login Settings**:
   - **Root URL**: `http://localhost:4200`
   - **Home URL**: `http://localhost:4200`
   - **Valid redirect URIs**:
     - `http://localhost:4200/*`
     - `http://localhost:4200`
   - **Valid post logout redirect URIs**:
     - `http://localhost:4200/*`
     - `http://localhost:4200`
   - **Web origins**:
     - `+` (or `http://localhost:4200`)
5. Click **Save**.

### Assign Default Client Scopes
1. In the client configuration, go to the **Client Scopes** tab.
2. Click **Add client scope**.
3. Select the 5 scopes (`notification:write`, `notification:read`, `audit:read`, `user:read`, `user:admin`).
4. Choose **Default** so they are automatically included in issued JWT access tokens.

---

## 5. User Creation and Role Assignment

1. Navigate to **Users** in the left menu and click **Add user**.
2. Fill in:
   - **Username**: `aegis-dev`
   - **Email**: `aegis-dev@example.local`
   - **Email verified**: **ON**
   - **Enabled**: **ON**
3. Click **Create**.
4. Go to the **Credentials** tab:
   - Click **Set password**.
   - **Password**: `dev123`
   - **Temporary**: **OFF**
   - Click **Save** and confirm.

---

## 6. Frontend Environment Configuration

Ensure the frontend environment configuration files match your running Keycloak instance:

### `src/environments/environment.ts` & `src/environments/environment.development.ts`

```typescript
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080',
  keycloakUrl: 'http://localhost:8088', // Match your Keycloak host/port
  keycloakRealm: 'aegis',
  keycloakClientId: 'aegis-dev-cli'
};
```

### `.env` & `.env.development`

```env
API_BASE_URL=http://localhost:8080
KEYCLOAK_URL=http://localhost:8088
KEYCLOAK_REALM=aegis
KEYCLOAK_CLIENT_ID=aegis-dev-cli
```

---

## 7. Security Considerations & Best Practices

1. **No Client Secrets in Frontend**: As a Single Page Application (SPA), the Angular frontend cannot securely store client secrets. Always configure the client as a **Public Client** (`Client authentication: OFF`) and use **PKCE** (Proof Key for Code Exchange).
2. **Strict Web Origins & Redirect URIs**: In production, replace `http://localhost:4200` with your production domains (e.g. `https://admin.aegisnotify.com`). Never use wildcard `*` for redirect URIs.
3. **Bearer Token Relay**: Outgoing HTTP calls to the backend (`aegis-api-gateway` on `http://localhost:8080`) are intercepted by `includeBearerTokenInterceptor` provided by `keycloak-angular`, automatically appending `Authorization: Bearer <JWT>`.
4. **Token Refresh**: Token lifecycle and silent refresh are handled automatically via `withAutoRefreshToken` in `app.config.ts`.

---

## 8. Verifying the Authentication Flow

1. Start Keycloak:
   ```bash
   docker compose up -d keycloak
   ```
2. Start the Angular application:
   ```bash
   pnpm start
   ```
3. Open `http://localhost:4200` in your browser.
4. The application will initialize `provideKeycloak()` and authenticate with the `aegis` realm.
5. In development with `onLoad: 'check-sso'` or `'login-required'`, the application validates session state and transmits the token to backend API endpoints.
