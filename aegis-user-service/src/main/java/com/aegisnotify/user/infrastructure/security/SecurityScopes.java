package com.aegisnotify.user.infrastructure.security;

/**
 * OAuth2 scope literals enforced by {@code aegis-user-service}.
 *
 * <p>Values MUST match {@code docs/security/scopes.md} and {@code
 * docker/keycloak/aegis-realm.json} byte-for-byte. {@code user:read} was
 * declared in Slice 5a; {@code user:admin} is added in Slice 5b for the
 * mutation endpoints that require it. The two are deliberately
 * non-hierarchical (D3): holding one never implicitly grants the other.</p>
 */
public final class SecurityScopes {

  public static final String USER_READ = "user:read";
  public static final String USER_ADMIN = "user:admin";

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
