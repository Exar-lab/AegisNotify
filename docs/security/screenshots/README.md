# Keycloak Admin Console Screenshots

This directory holds reference screenshots of the local Keycloak admin console,
linked from the root [`README.md`](../../../README.md#local-authentication-keycloak)
"Local authentication (Keycloak)" section.

The root README currently links these placeholder filenames. Replace each one
with a real capture (same filename, `.png`) taken against the `aegis` realm
started by `docker compose up -d keycloak`, then remove the corresponding
`<!-- TODO: user to replace with real screenshot -->` comment in the README.

| File | What to capture |
| --- | --- |
| `TODO-realm-overview.png` | Admin console, `aegis` realm overview / general settings page |
| `TODO-client-scopes.png` | Admin console, realm's client scopes list (all 5: `notification:write`, `notification:read`, `audit:read`, `user:read`, `user:admin`) |
| `TODO-dev-cli-client.png` | Admin console, the `aegis-dev-cli` client's settings page |
| `TODO-dev-user.png` | Admin console, the `aegis-dev` user's details page |
| `TODO-token-response.png` | A successful password-grant token response (e.g. terminal output of the documented `curl` command, or an API client showing the JSON body) |

Missing screenshots do not block merging this documentation — they can be added in a
follow-up commit; the placeholders above are accepted until real captures are supplied.
