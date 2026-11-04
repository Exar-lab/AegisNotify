{
  "realm": "aegis",
  "enabled": true,
  "sslRequired": "external",
  "registrationAllowed": false,
  "loginTheme": "aegis",
  "clientScopes": [
    {
      "name": "notification:write",
      "description": "Submit notifications for delivery (docs/security/scopes.md)",
      "protocol": "openid-connect",
      "attributes": {
        "include.in.token.scope": "true",
        "display.on.consent.screen": "false"
      }
    },
    {
      "name": "notification:read",
      "description": "Read notification delivery status (docs/security/scopes.md)",
      "protocol": "openid-connect",
      "attributes": {
        "include.in.token.scope": "true",
        "display.on.consent.screen": "false"
      }
    },
    {
      "name": "audit:read",
      "description": "Read the audit trail (docs/security/scopes.md)",
      "protocol": "openid-connect",
      "attributes": {
        "include.in.token.scope": "true",
        "display.on.consent.screen": "false"
      }
    },
    {
      "name": "user:read",
      "description": "List/query managed users (docs/security/scopes.md, forward-looking)",
      "protocol": "openid-connect",
      "attributes": {
        "include.in.token.scope": "true",
        "display.on.consent.screen": "false"
      }
    },
    {
      "name": "user:admin",
      "description": "Create/update/disable managed users (docs/security/scopes.md, forward-looking)",
      "protocol": "openid-connect",
      "attributes": {
        "include.in.token.scope": "true",
        "display.on.consent.screen": "false"
      }
    }
  ],
  "clients": [
    {
      "clientId": "aegis-dev-cli",
      "name": "Demo password-grant client",
      "description": "Lets the demo script fetch a token with curl (docs/deploy-vm.md). Password grant on a public client: delete this client from the admin console once the demo no longer needs it.",
      "enabled": true,
      "publicClient": true,
      "standardFlowEnabled": false,
      "implicitFlowEnabled": false,
      "directAccessGrantsEnabled": true,
      "serviceAccountsEnabled": false,
      "defaultClientScopes": [
        "notification:write",
        "notification:read",
        "audit:read",
        "user:read",
        "user:admin"
      ]
    },
    {
      "clientId": "aegis-user-service",
      "name": "aegis-user-service Admin REST client",
      "description": "Confidential service-account client used by aegis-user-service to call the Keycloak Admin REST API. Granted view-users and manage-users. The secret is rendered from KEYCLOAK_ADMIN_CLIENT_SECRET.",
      "enabled": true,
      "publicClient": false,
      "standardFlowEnabled": false,
      "implicitFlowEnabled": false,
      "directAccessGrantsEnabled": false,
      "serviceAccountsEnabled": true,
      "secret": "__USER_SERVICE_SECRET__",
      "defaultClientScopes": []
    },
    {
      "clientId": "aegis-admin-frontend",
      "name": "Aegis Admin Frontend",
      "description": "Public OpenID Connect client used by the Angular admin frontend. Authorization Code Flow with PKCE. Redirect URIs and web origin are rendered from FRONTEND_ORIGIN.",
      "enabled": true,
      "publicClient": true,
      "standardFlowEnabled": true,
      "implicitFlowEnabled": false,
      "directAccessGrantsEnabled": false,
      "serviceAccountsEnabled": false,
      "redirectUris": [
        "__FRONTEND_ORIGIN__/*",
        "__FRONTEND_ORIGIN__/silent-check-sso.html",
        "__FRONTEND_ORIGIN__"
      ],
      "webOrigins": [
        "__FRONTEND_ORIGIN__"
      ],
      "defaultClientScopes": [
        "notification:read",
        "notification:write",
        "audit:read",
        "user:read",
        "user:admin"
      ]
    }
  ],
  "users": [
    {
      "username": "aegis-dev",
      "email": "aegis-dev@example.local",
      "firstName": "Aegis",
      "lastName": "Demo",
      "enabled": true,
      "emailVerified": true,
      "credentials": [
        {
          "type": "password",
          "value": "__DEMO_USER_PASSWORD__",
          "temporary": false
        }
      ]
    },
    {
      "username": "service-account-aegis-user-service",
      "enabled": true,
      "serviceAccountClientId": "aegis-user-service",
      "clientRoles": {
        "realm-management": ["view-users", "manage-users"]
      }
    }
  ]
}
