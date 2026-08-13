package com.aegisnotify.notification.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Canary test for {@link SecurityScopes} literals.
 *
 * <p>This test intentionally duplicates the scope strings instead of parsing
 * {@code docs/security/scopes.md} or {@code docker/keycloak/aegis-realm.json}.
 * If a scope literal changes here without a matching update to those files,
 * this drift is the signal to reconcile {@code docs/security/scopes.md} and
 * {@code docker/keycloak/aegis-realm.json} with the code.</p>
 */
class SecurityScopesTest {

  @Test
  void notificationWrite_matchesDocumentedScopeContract() {
    assertThat(SecurityScopes.NOTIFICATION_WRITE).isEqualTo("notification:write");
  }

  @Test
  void notificationRead_matchesDocumentedScopeContract() {
    assertThat(SecurityScopes.NOTIFICATION_READ).isEqualTo("notification:read");
  }

  @Test
  void authority_prependsScopePrefix() {
    assertThat(SecurityScopes.authority(SecurityScopes.NOTIFICATION_READ))
        .isEqualTo("SCOPE_notification:read");
  }

  @Test
  void authority_prependsScopePrefix_forWriteScope() {
    assertThat(SecurityScopes.authority(SecurityScopes.NOTIFICATION_WRITE))
        .isEqualTo("SCOPE_notification:write");
  }
}
