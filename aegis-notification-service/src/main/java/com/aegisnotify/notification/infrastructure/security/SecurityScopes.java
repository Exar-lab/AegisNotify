package com.aegisnotify.notification.infrastructure.security;

/**
 * OAuth2 scope literals enforced by {@code aegis-notification-service}.
 *
 * <p>Values MUST match {@code docs/security/scopes.md} and
 * {@code docker/keycloak/aegis-realm.json} byte-for-byte. A dedicated canary
 * test ({@code SecurityScopesTest}) duplicates these literals to catch drift.</p>
 */
public final class SecurityScopes {

  public static final String NOTIFICATION_WRITE = "notification:write";
  public static final String NOTIFICATION_READ = "notification:read";

  private SecurityScopes() {
  }

  /**
   * Prepends the {@code SCOPE_} prefix Spring Security expects for JWT
   * scope-derived authorities.
   *
   * @param scope the unprefixed scope literal, e.g. {@code "notification:read"}
   * @return the Spring Security authority, e.g. {@code "SCOPE_notification:read"}
   */
  public static String authority(String scope) {
    return "SCOPE_" + scope;
  }
}
