package com.aegisnotify.gateway.security;

/**
 * OAuth2 scope literals enforced by {@code aegis-api-gateway}.
 *
 * <p>The gateway is the single source of truth consumed by both the reactive
 * {@code authorizeExchange} matchers and {@code ScopeAwareAccessDeniedHandler}.
 * {@code USER_READ} and {@code USER_ADMIN} are forward-looking: their routes
 * are not yet proxied (the {@code aegis-user-service} module ships in a later
 * slice), but the scope literals are reserved here so the realm and docs
 * contract can be authored once. Values MUST match
 * {@code docs/security/scopes.md} and {@code docker/keycloak/aegis-realm.json}
 * byte-for-byte. A dedicated canary test ({@code SecurityScopesTest})
 * duplicates these literals to catch drift.</p>
 */
public final class SecurityScopes {

  public static final String NOTIFICATION_WRITE = "notification:write";
  public static final String NOTIFICATION_READ = "notification:read";
  public static final String AUDIT_READ = "audit:read";
  public static final String USER_READ = "user:read";
  public static final String USER_ADMIN = "user:admin";

  private SecurityScopes() {
  }

  /**
   * Prepends the {@code SCOPE_} prefix Spring Security expects for JWT
   * scope-derived authorities.
   *
   * @param scope the unprefixed scope literal, e.g. {@code "audit:read"}
   * @return the Spring Security authority, e.g. {@code "SCOPE_audit:read"}
   */
  public static String authority(String scope) {
    return "SCOPE_" + scope;
  }
}
