package com.aegisnotify.user.infrastructure.security;

/**
 * OAuth2 scope literals enforced by {@code aegis-user-service}.
 *
 * <p>Values MUST match {@code docs/security/scopes.md} and {@code
 * docker/keycloak/aegis-realm.json} byte-for-byte. Only {@code user:read} is
 * declared in Slice 5a; {@code user:admin} is added in Slice 5b when the
 * mutation endpoints that require it land, matching every sibling service's
 * "declare only the scopes you enforce" convention.</p>
 */
public final class SecurityScopes {

  public static final String USER_READ = "user:read";

  private SecurityScopes() {
  }

  /**
   * Prepends the {@code SCOPE_} prefix Spring Security expects for JWT
   * scope-derived authorities.
   *
   * @param scope the unprefixed scope literal, e.g. {@code "user:read"}
   * @return the Spring Security authority, e.g. {@code "SCOPE_user:read"}
   */
  public static String authority(String scope) {
    return "SCOPE_" + scope;
  }
}
