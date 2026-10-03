# Keycloak desde cero para el frontend OIDC de AegisNotify

Configuración local paso a paso de Keycloak 26 para que un frontend en el navegador pueda autenticarse con Authorization Code + PKCE y llamar al API Gateway con un JWT Bearer. Los valores siguientes provienen de este repositorio: [`docker-compose.yml`](docker-compose.yml), [`docker/keycloak/aegis-realm.json`](docker/keycloak/aegis-realm.json), [`docs/security/scopes.md`](docs/security/scopes.md), [`README.md`](README.md) y [`Keycloak.md`](Keycloak.md).

Esta guía no describe el código de la aplicación bajo `aegis-admin-frontend`. Solo enumera los valores de Keycloak y del entorno que debe usar un frontend.

Compose publica Keycloak en el **puerto 8088** (`8088:8080`). No uses `8080` (API Gateway) ni `8081` como URL de Keycloak a menos que cambies la asignación de puertos.

## 1. Requisitos previos

- Docker y Docker Compose
- Opcional: `curl` y `jq` para probar tokens rápidamente
- Trabaja desde la **raíz del repositorio** para que `./docker/keycloak` se monte correctamente

## 2. Iniciar Keycloak

```bash
docker compose up -d keycloak
```

Lo que inicia Compose:

| Configuración | Valor |
| --- | --- |
| Imagen | `quay.io/keycloak/keycloak:26.0` |
| Nombre del contenedor | `aegis-keycloak` |
| Comando | `start-dev --import-realm` |
| Consola de administración | `http://localhost:8088` |
| Administrador inicial | `admin` / `admin` (sobrescribe con `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD`) |
| Volumen de importación | `./docker/keycloak` → `/opt/keycloak/data/import` (solo lectura) |

Espera hasta que el proceso esté activo y confirma el realm:

```bash
curl -s http://localhost:8088/realms/aegis/.well-known/openid-configuration
```

Abre la Admin Console en `http://localhost:8088` e inicia sesión como `admin` / `admin`. Esas credenciales son solo para desarrollo local.

## 3. Realm: importar (recomendado) o crear manualmente

### 3.1 Ruta de importación (ya realizada por Compose)

`--import-realm` carga [`docker/keycloak/aegis-realm.json`](docker/keycloak/aegis-realm.json). Después de un inicio correcto ya deberías tener:

- Realm **`aegis`**, habilitado, `sslRequired` = `none` (solo HTTP local)
- Client scopes: `notification:write`, `notification:read`, `audit:read`, `user:read`, `user:admin`
- Cliente público **`aegis-dev-cli`** (Direct Access Grants / password grant)
- Cliente confidencial **`aegis-user-service`** (cuenta de servicio para la Admin API)
- Usuario **`aegis-dev`** (consulta la sección 7)

Ve a la sección 4, salvo que necesites un realm creado únicamente en la consola.

### 3.2 Realm manual (sin importación)

1. En la Admin Console, abre el menú desplegable de realms (arriba a la izquierda) y elige **Create realm**.
2. Establece **Realm name** en `aegis`, déjalo **Enabled** y créalo.
3. Abre **Realm settings** → **General**. Para HTTP local establece **Require SSL** en **None**. No uses esto en producción.
4. Abre **Client scopes** → **Create client scope** y crea cada uno de los cinco scopes siguientes. Usa **Protocol** `openid-connect`. Después de guardar, abre el scope → **Settings** (o atributos) y establece **Include in token scope** (`include.in.token.scope`) en **true**. No cambies los nombres; el gateway y los servicios coinciden exactamente con estos literales ([`docs/security/scopes.md`](docs/security/scopes.md)).

| Scope | Propósito |
| --- | --- |
| `notification:write` | `POST /api/v1/notifications` |
| `notification:read` | `GET /api/v1/notifications/{id}/status` |
| `audit:read` | `/api/v1/audit/**` |
| `user:read` | Listado/consulta de usuarios (asignar solo si la interfaz llama a esas API) |
| `user:admin` | Crear/actualizar/deshabilitar/cambiar contraseña de usuarios (asignar solo si la interfaz llama a esas API) |

## 4. Clientes que ya existen en el realm importado

**No** dirijas una aplicación de navegador a estos clientes tal como están.

**`aegis-dev-cli`**

- Cliente público usado por el `curl` del password-grant de README
- Direct Access Grants habilitado; Standard flow deshabilitado en el JSON del realm
- Adecuado para pruebas rápidas de CLI/backend, no para Authorization Code en el navegador a menos que añadas las URI de redirección y Standard flow manualmente

**`aegis-user-service`**

- Cliente confidencial con un secreto local `local-dev-only-secret`
- Roles de cuenta de servicio `view-users` y `manage-users` en `realm-management`
- Usado por `aegis-user-service` mediante `KEYCLOAK_ADMIN_CLIENT_ID` / `KEYCLOAK_ADMIN_CLIENT_SECRET`
- Nunca pongas este secreto en JavaScript del frontend

Si creaste el realm manualmente, recrea `aegis-dev-cli` solo si necesitas la prueba rápida de password-grant de la sección 9.

## 5. Crear el cliente OIDC del frontend

La SPA debe usar un cliente público dedicado. En Keycloak 26:

1. Selecciona el realm **`aegis`**.
2. **Clients** → **Create client**.
3. **General settings**
   - **Client type**: OpenID Connect
   - **Client ID**: `aegis-admin-frontend`
   - **Name**: `AegisNotify Admin Frontend` (nombre visible opcional)
4. **Capability config**
   - **Client authentication**: Off (cliente público; sin secreto)
   - **Standard flow**: On (Authorization Code)
   - **Direct access grants**: Off
   - **Implicit flow**: Off
5. **Login settings**
   - **Valid redirect URIs**: `http://localhost:4200/*`
   - **Valid post logout redirect URIs**: `http://localhost:4200/*`
   - **Web origins**: `http://localhost:4200` (CORS para el endpoint de tokens; no uses `*` en producción)
6. Guarda y abre la pestaña **Advanced** del cliente.
7. Establece **Proof Key for Code Exchange Code Challenge Method** (PKCE) en **S256**. En blanco se permite PKCE, pero no se exige; S256 es lo que debe imponer una SPA pública.

El patrón de redirección local recomendado es `http://localhost:4200/*`. El callback exacto suele ser `http://localhost:4200/` o la ruta del router que usa la aplicación. En producción usa URI HTTPS exactas, no comodines.

## 6. Asociar scopes al cliente del frontend

On client `aegis-admin-frontend` → **Client scopes**:

Añade como client scopes **Default** (el claim `scope` del access token debe contener estas cadenas):

- `notification:read`
- `notification:write`
- `audit:read`

Añade `user:read` y `user:admin` solo si esta interfaz llamará a API de administración de usuarios. Esos scopes no son jerárquicos: `user:admin` no concede `user:read`.

Mantén los scopes personalizados como client scopes de OpenID Connect con `include.in.token.scope=true`, de acuerdo con [`docs/security/scopes.md`](docs/security/scopes.md).

## 7. Usuario de desarrollo

Si el realm fue importado, este usuario ya existe:

```text
Username: aegis-dev
Password:  dev123
Email:     aegis-dev@example.local
Enabled:   yes
Email verified: yes
```

Para crearlo manualmente: **Users** → **Create new user** → nombre de usuario `aegis-dev`, correo electrónico como el anterior, **Email verified** activado, **Enabled** activado → **Credentials** → establece la contraseña `dev123`, **Temporary** desactivado. Solo para desarrollo local.

## 8. Valores que debe usar el frontend

Issuer (autoridad OIDC):

```text
http://localhost:8088/realms/aegis
```

| Variable / configuración | Valor local |
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

Endpoints relacionados:

| Endpoint | URL |
| --- | --- |
| Well-known | `http://localhost:8088/realms/aegis/.well-known/openid-configuration` |
| Token | `http://localhost:8088/realms/aegis/protocol/openid-connect/token` |
| JWKS | `http://localhost:8088/realms/aegis/protocol/openid-connect/certs` |

Comportamiento de la aplicación:

- Usa **Authorization Code + PKCE (S256)**. Redirige al usuario al inicio de sesión de Keycloak; no integres password grant en el navegador.
- Envía `Authorization: Bearer <access_token>` al API Gateway en `http://localhost:8080`.
- Cierra la sesión mediante Keycloak y vuelve a una URI de post-logout registrada.
- No llames a la Keycloak Admin REST API desde el navegador. La administración de usuarios pasa por servicios backend que usan `aegis-user-service`.

`KEYCLOAK_CLIENT_ID=aegis-dev-cli` solo es válido para el cliente CLI existente. Para una SPA, usa preferentemente `aegis-admin-frontend` tal como se configuró arriba.

## 9. Verificar tokens (prueba rápida del backend, no de la SPA)

Password grant contra `aegis-dev-cli` (Standard flow está desactivado en ese cliente; esto es solo para CLI):

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

El segundo comando necesita que el gateway esté en ejecución. Para la aplicación Angular (u otra SPA), confirma el inicio de sesión mediante la redirección del navegador a Keycloak con PKCE, no con este curl.

## 10. Reimportación y producción

Reimportación limpia (destruye el volumen de Compose de Keycloak y **sobrescribe** los cambios realizados únicamente en la consola):

```bash
docker compose down -v
docker compose up -d keycloak
```

Si rotas el secreto de `aegis-user-service`, actualiza `KEYCLOAK_ADMIN_CLIENT_SECRET` en el servicio de usuarios y [`docker/keycloak/aegis-realm.json`](docker/keycloak/aegis-realm.json) si el valor debe sobrevivir a la reimportación.

Producción: no uses `start-dev`, HTTP, `sslRequired=none`, `admin`/`admin`, `dev123` ni `local-dev-only-secret`. Usa HTTPS, clientes específicos por entorno, URI de redirección exactas, secretos fuera del repositorio y una URI de JWKS que coincida con el realm de producción.

## Fuentes del proyecto

- [`docker-compose.yml`](docker-compose.yml)
- [`docker/keycloak/aegis-realm.json`](docker/keycloak/aegis-realm.json)
- [`README.md`](README.md)
- [`docs/security/scopes.md`](docs/security/scopes.md)
- [`Keycloak.md`](Keycloak.md)
- [`aegis-user-service/src/main/resources/application.yml`](aegis-user-service/src/main/resources/application.yml)
