# Postman collection

`AegisNotify.postman_collection.json` covers the 23 requests exposed by `aegis-notification-service`,
`aegis-user-service`, and `aegis-audit-service`, generated from their live OpenAPI specs
(issue #169). The gateway's `/docs/<service>/**` routes that serve those specs only exist
under the `local` Spring profile and are never reachable in a normal deployment — see
[`aegis-api-gateway`'s `ApiDocsConfig`](../../aegis-api-gateway/src/main/java/com/aegisnotify/gateway/config/ApiDocsConfig.java).

## Using the collection

1. Import `AegisNotify.postman_collection.json` into Postman.
2. Set the collection's `baseUrl` variable to your gateway's address (defaults to
   `http://localhost:8080`).
3. Get a dev access token and paste it into the collection's `bearerToken` variable — every
   request inherits collection-level Bearer auth, so this is the only auth step needed:

   ```bash
   ACCESS_TOKEN=$(curl -s -X POST http://localhost:8088/realms/aegis/protocol/openid-connect/token \
     -d "grant_type=password" \
     -d "client_id=aegis-dev-cli" \
     -d "username=aegis-dev" \
     -d "password=dev123" \
     -d "scope=notification:write notification:read audit:read user:read user:admin" \
     | jq -r .access_token)
   ```

   (Same client this repo's root README already documents for local JWT retrieval —
   `aegis-dev-cli`/`aegis-dev` exist only for local development.)

## Regenerating

The collection is a point-in-time snapshot; regenerate it after an API contract changes rather
than hand-editing it.

1. Start the full local stack with the `local` profile active on the gateway and the three
   backend services (`SPRING_PROFILES_ACTIVE=local`) — see the root README's "Local development"
   section for infra/service startup order. The `local` profile is what turns the
   `/docs/**` routes on.
2. Pull the three specs through the gateway:

   ```bash
   mkdir -p /tmp/aegis-openapi && cd /tmp/aegis-openapi
   curl -s http://localhost:8080/docs/notification/v3/api-docs -o notification.json
   curl -s http://localhost:8080/docs/user/v3/api-docs -o user.json
   curl -s http://localhost:8080/docs/audit/v3/api-docs -o audit.json
   ```

3. Convert each to a Postman collection:

   ```bash
   npx openapi-to-postmanv2 -s notification.json -o notification.postman.json -p
   npx openapi-to-postmanv2 -s user.json -o user.postman.json -p
   npx openapi-to-postmanv2 -s audit.json -o audit.postman.json -p
   ```

4. Merge the three `item` arrays into one collection under folders named "Notification
   Service" / "User Service" / "Audit Service", with a single shared `baseUrl` variable
   (not each sub-collection's own, which points at its `/docs/<service>` proxy path rather
   than the real `/api/v1/**` routes) and collection-level Bearer auth using a `bearerToken`
   variable. There's no saved script for this merge step — it's a handful of lines of JSON
   surgery (see `AegisNotify.postman_collection.json`'s `info`/`auth`/`variable`/`item`
   structure for the shape to reproduce).
