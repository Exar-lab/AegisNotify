package com.aegisnotify.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class SecurityConfigTest {

  @Autowired
  WebTestClient webTestClient;

  @MockBean
  ReactiveJwtDecoder jwtDecoder;

  @Test
  void actuatorHealthIsPublic() {
    webTestClient.get().uri("/actuator/health")
        .exchange()
        .expectStatus().isOk();
  }

  @Test
  void actuatorInfoIsPublic() {
    webTestClient.get().uri("/actuator/info")
        .exchange()
        .expectStatus().isOk();
  }

  @Test
  void notificationSubmitRequiresAuth() {
    webTestClient.post().uri("/api/v1/notifications")
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  void statusEndpointRequiresAuth() {
    webTestClient.get().uri("/api/v1/notifications/some-id/status")
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  void statusEndpoint_withNotificationReadScope_isNotForbidden() {
    // No live aegis-notification-service backend in this test context (see
    // application-test.yml), so a scoped request that clears the security layer
    // fails downstream at routing (connection refused -> 500), never at 401/403.
    // Asserting 5xx here — rather than merely "not 403" — proves the request
    // actually passed authorization instead of failing for some other reason.
    webTestClient.mutateWith(mockJwt().authorities(() -> "SCOPE_notification:read"))
        .get().uri("/api/v1/notifications/some-id/status")
        .exchange()
        .expectStatus().is5xxServerError();
  }

  @Test
  void notificationSubmit_withNotificationWriteScope_isNotForbidden() {
    // Symmetric positive-scope coverage for POST /api/v1/notifications: same
    // no-live-backend situation as the status endpoint above, so a scoped
    // request clears security and fails downstream with a 5xx, not 401/403.
    webTestClient.mutateWith(mockJwt().authorities(() -> "SCOPE_notification:write"))
        .post().uri("/api/v1/notifications")
        .exchange()
        .expectStatus().is5xxServerError();
  }

  @Test
  void statusEndpoint_withoutNotificationReadScope_returns403WithRequiredScope() {
    webTestClient.mutateWith(mockJwt().authorities(() -> "SCOPE_notification:write"))
        .get().uri("/api/v1/notifications/some-id/status")
        .exchange()
        .expectStatus().isForbidden()
        .expectBody()
        .jsonPath("$.requiredScope").isEqualTo("notification:read")
        .jsonPath("$.sub").doesNotExist()
        .jsonPath("$.iss").doesNotExist();
  }

  @Test
  void auditEndpoint_withoutAuditReadScope_returns403WithRequiredScope() {
    webTestClient.mutateWith(mockJwt().authorities(() -> "SCOPE_notification:read"))
        .get().uri("/api/v1/audit")
        .exchange()
        .expectStatus().isForbidden()
        .expectBody()
        .jsonPath("$.requiredScope").isEqualTo("audit:read");
  }

  @Test
  void usersEndpoint_withoutUserReadScope_returns403WithRequiredScope() {
    // aegis-user-service isn't proxied yet (see RouteScopeRules javadoc), but the
    // security layer must still reject a missing-scope request with 403 before
    // Spring Cloud Gateway ever attempts to route it, same as the audit route above.
    webTestClient.mutateWith(mockJwt().authorities(() -> "SCOPE_notification:read"))
        .get().uri("/api/v1/users/123")
        .exchange()
        .expectStatus().isForbidden()
        .expectBody()
        .jsonPath("$.requiredScope").isEqualTo("user:read");
  }

  @Test
  void unmappedApiPath_withAuthenticatedJwtAndNoScope_isNotRejectedForMissingScope() {
    // /api/v1/does-not-exist matches no RouteScopeRule, so it falls through to the
    // generic /api/v1/** .authenticated() catch-all in SecurityConfig: any
    // authenticated JWT — no specific scope required — clears the security layer.
    // It then fails at Gateway routing (no matching route predicate -> 404), which
    // is a distinct failure mode from the scope-based 403 this class asserts elsewhere.
    webTestClient.mutateWith(mockJwt())
        .get().uri("/api/v1/does-not-exist")
        .exchange()
        .expectStatus().isNotFound();
  }

  @Test
  void malformedToken_returns401NotForbidden() {
    when(jwtDecoder.decode(anyString()))
        .thenReturn(Mono.error(new BadJwtException("malformed token")));

    webTestClient.get().uri("/api/v1/notifications/some-id/status")
        .header("Authorization", "Bearer not-a-valid-jwt")
        .exchange()
        .expectStatus().isUnauthorized();
  }
}
