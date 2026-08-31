package com.aegisnotify.user.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Canary test for {@link SecurityScopes} literals.
 *
 * <p>This test intentionally duplicates the scope strings instead of parsing
 * {@code docs/security/scopes.md} or {@code docker/keycloak/aegis-realm.json}.
 * If a scope literal changes here without a matching update to those files,
 * this drift is the signal to reconcile {@code docs/security/scopes.md} and
 * {@code docker/keycloak/aegis-realm.json} with the code. Added in Slice 5b
 * once both {@code user:read} and {@code user:admin} were declared, matching
 * every sibling service's drift-canary convention (deferred from Slice 5a as
 * a documented SUGGESTION).</p>
 */
class SecurityScopesTest {

  @Test
  void userRead_matchesDocumentedScopeContract() {
    assertThat(SecurityScopes.USER_READ).isEqualTo("user:read");
  }

  @Test
  void userAdmin_matchesDocumentedScopeContract() {
    assertThat(SecurityScopes.USER_ADMIN).isEqualTo("user:admin");
  }

  @Test
  void authority_prependsScopePrefix_forReadScope() {
    assertThat(SecurityScopes.authority(SecurityScopes.USER_READ))
        .isEqualTo("SCOPE_user:read");
  }

  @Test
  void authority_prependsScopePrefix_forAdminScope() {
    assertThat(SecurityScopes.authority(SecurityScopes.USER_ADMIN))
        .isEqualTo("SCOPE_user:admin");
  }
}
