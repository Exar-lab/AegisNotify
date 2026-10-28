# Configuración de Keycloak para AegisNotify

Esta guía configura autenticación OIDC para el frontend Angular y acceso JWT al API Gateway. Los valores marcados como locales proceden de la configuración existente del proyecto. El realm JSON y el `docker-compose.yml` son las fuentes de verdad para la inicialización local.

## 1. Valores reales del proyecto

| Elemento | Valor comprobado |
| --- | --- |
| Imagen de Keycloak | `quay.io/keycloak/keycloak:26.0` |
| URL local publicada por Docker Compose | `http://localhost:8088` |
| Realm | `aegis` |
| Endpoint OIDC | `http://localhost:8088/realms/aegis` |
| JWKS | `http://localhost:8088/realms/aegis/protocol/openid-connect/certs` |
| Token endpoint | `http://localhost:8088/realms/aegis/protocol/openid-connect/token` |
| Cliente CLI existente | `aegis-dev-cli` |
| Cliente Admin API existente | `aegis-user-service` |
| API Gateway del frontend | `http://localhost:8080` |
| Variables del frontend | `KEYCLOAK_REALM=aegis`, `KEYCLOAK_CLIENT_ID=aegis-dev-cli` |

La configuración del frontend contiene `KEYCLOAK_URL=http://localhost:8081`, pero el Compose del proyecto publica Keycloak en el puerto `8088`. Para este repositorio debe usarse `http://localhost:8088`, salvo que se cambie explícitamente el mapeo de puertos.

No existe actualmente una redirect URI registrada ni una integración OIDC terminada en el frontend. Para el servidor de desarrollo Angular se debe registrar la siguiente URI recomendada:

```text
http://localhost:4200/*
```

La URI exacta de retorno será normalmente `http://localhost:4200/` o la ruta que use el router. En producción debe sustituirse por el origen HTTPS real y no conservar el comodín.

## 2. Opción recomendada: importar el realm existente

Desde la raíz del repositorio, inicia Keycloak:

```bash
docker compose up -d keycloak
```

El contenedor ejecuta `start-dev --import-realm` y monta `docker/keycloak` como directorio de importación. Comprueba que el realm responde:

```bash
curl -s http://localhost:8088/realms/aegis/.well-known/openid-configuration
```

Accede a la consola de administración en `http://localhost:8088` con las credenciales locales predeterminadas `admin` / `admin`. Estas credenciales son solo para desarrollo y deben cambiarse fuera de un entorno local.

El archivo [`docker/keycloak/aegis-realm.json`](docker/keycloak/aegis-realm.json) inicializa:

- El realm `aegis`, habilitado y con `sslRequired` igual a `none` para desarrollo.
- Los scopes `notification:write`, `notification:read`, `audit:read`, `user:read` y `user:admin`.
- El cliente público `aegis-dev-cli`, con Direct Access Grants habilitado.
- El usuario local `aegis-dev`, con contraseña `dev123`.
- El cliente confidencial `aegis-user-service`, con service account y roles `view-users` y `manage-users`.

El cliente `aegis-dev-cli` existe para obtener tokens mediante password grant desde la documentación de backend. Para una aplicación Angular en el navegador es preferible crear un cliente público dedicado, descrito en el paso siguiente, con Authorization Code + PKCE.

## 3. Crear el cliente OIDC del frontend

En la consola de Keycloak:

1. Selecciona el realm `aegis`.
2. Abre **Clients** y pulsa **Create client**.
3. Usa estos valores:
   - **Client type**: `OpenID Connect`.
   - **Client ID**: `aegis-admin-frontend`.
   - **Name**: `AegisNotify Admin Frontend`.
4. En las capacidades del cliente configura:
   - **Client authentication**: desactivado. El frontend es un cliente público y no debe contener un secreto.
   - **Standard flow**: activado.
   - **Direct access grants**: desactivado para el flujo normal del frontend.
   - **Implicit flow**: desactivado.
5. En **Login settings** configura:
   - **Valid redirect URIs**: `http://localhost:4200/*`.
   - **Valid post logout redirect URIs**: `http://localhost:4200/*`.
   - **Web origins**: `http://localhost:4200`.
6. En **Advanced** selecciona PKCE con método `S256`, si la versión de la consola muestra esta opción.
7. Guarda el cliente.

No reutilices el secreto de `aegis-user-service` en el frontend. Ese cliente es confidencial y su secreto local es `local-dev-only-secret`; exponerlo en JavaScript permitiría que cualquier usuario lo obtuviera.

## 4. Asociar scopes al cliente frontend

En el cliente `aegis-admin-frontend`, abre **Client scopes** y añade como scopes por defecto los que necesite la interfaz:

- `notification:read` para consultar notificaciones y estados.
- `notification:write` para enviar notificaciones.
- `audit:read` para consultar auditoría.

Los scopes `user:read` y `user:admin` existen en el realm y están previstos para administración de usuarios. Asígnalos únicamente si este frontend va a consumir esas operaciones.

Mantén los scopes personalizados como client scopes de tipo OpenID Connect con `include.in.token.scope=true`, tal como exige [`docs/security/scopes.md`](docs/security/scopes.md). El backend espera esos literales exactamente y aplica autorización adicional en el gateway y en los servicios.

## 5. Crear o comprobar el usuario de desarrollo

Si el realm fue importado, ya existe:

```text
Usuario: aegis-dev
Contraseña: dev123
Email: aegis-dev@example.local
Estado: habilitado
Email verificado: sí
```

Si se crea manualmente, en **Users** añade el usuario `aegis-dev`, habilítalo, marca el email como verificado y establece una contraseña permanente `dev123` únicamente para desarrollo local.

## 6. Configurar las variables del frontend

Para el frontend local, usa el puerto real publicado por Compose:

```dotenv
API_BASE_URL=http://localhost:8080
KEYCLOAK_URL=http://localhost:8088
KEYCLOAK_REALM=aegis
KEYCLOAK_CLIENT_ID=aegis-admin-frontend
```

`KEYCLOAK_CLIENT_ID=aegis-dev-cli` solo corresponde al cliente CLI existente. Si el código frontend todavía está implementado para usar ese cliente, puede funcionar como configuración provisional, pero debe registrarse también su redirect URI y la configuración de navegador. La opción recomendada es migrar al cliente público dedicado `aegis-admin-frontend`.

## 7. Parámetros OIDC que debe usar la aplicación

La aplicación debe construir el issuer a partir de:

```text
http://localhost:8088/realms/aegis
```

El flujo esperado es **Authorization Code + PKCE**. La aplicación debe redirigir al login de Keycloak, conservar el estado de la sesión, enviar el access token como `Authorization: Bearer <token>` al API Gateway y redirigir al usuario a Keycloak para cerrar sesión.

No debe usar el Admin REST API directamente desde el navegador. Las llamadas del frontend deben ir al gateway en `http://localhost:8080`; el gateway y los servicios validan el JWT usando el JWKS del realm.

## 8. Comprobar la obtención de un token

La documentación existente usa el cliente CLI y password grant para una prueba local del backend:

```bash
ACCESS_TOKEN=$(curl -s -X POST http://localhost:8088/realms/aegis/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=aegis-dev-cli" \
  -d "username=aegis-dev" \
  -d "password=dev123" \
  -d "scope=notification:write notification:read audit:read user:read user:admin" \
  | jq -r .access_token)
```

Comprueba el token contra el gateway:

```bash
curl -i http://localhost:8080/api/v1/notifications \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Para la aplicación Angular, valida el login mediante Authorization Code + PKCE; no copies este password grant al código del navegador.

## 9. Reimportar el realm

Si cambias `docker/keycloak/aegis-realm.json` y necesitas una importación limpia:

```bash
docker compose down -v
docker compose up -d keycloak
```

La importación puede sobrescribir cambios realizados manualmente en la consola. Si rotas el secreto del cliente `aegis-user-service`, actualiza también `KEYCLOAK_ADMIN_CLIENT_SECRET` del servicio y el JSON si deseas que el valor sobreviva a una reimportación.

## 10. Configuración de producción

No uses `start-dev`, HTTP, `sslRequired=none`, `admin/admin`, `dev123` ni `local-dev-only-secret` en producción. Usa HTTPS, un realm o instancia gestionada, clientes separados por entorno, redirect URIs exactas, secretos fuera del código y rotación de credenciales. El valor de `JWKS_URI` debe apuntar al endpoint HTTPS del realm de producción.

## Fuentes del proyecto

- [`docker-compose.yml`](docker-compose.yml): imagen, puerto, importación y credenciales bootstrap.
- [`docker/keycloak/aegis-realm.json`](docker/keycloak/aegis-realm.json): realm, scopes, clientes, usuarios y datos iniciales.
- [`README.md`](README.md): arranque local, token de prueba, scopes y variables de entorno.
- [`docs/security/scopes.md`](docs/security/scopes.md): contrato de scopes y reglas de autorización.
- [`aegis-user-service/src/main/resources/application.yml`](aegis-user-service/src/main/resources/application.yml): URL, realm y cliente de Admin API.
- `aegis-admin-frontend/.env` y `aegis-admin-frontend/.env.development`: API, URL, realm y cliente declarados por el frontend.

El análisis excluyó explícitamente el directorio `aegis-admin-service` y el archivo `assie.md`.