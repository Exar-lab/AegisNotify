package com.aegisnotify.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;

/**
 * Verifies the gateway's informative-but-safe 403 body (design decision A2):
 * it names the required scope and leaks no token, identity, or realm data.
 */
class ScopeAwareAccessDeniedHandlerTest {

  private final RouteScopeRules routeScopeRules = new RouteScopeRules();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ScopeAwareAccessDeniedHandler handler =
      new ScopeAwareAccessDeniedHandler(routeScopeRules, objectMapper);

  @Test
  void deniedStatusRequest_namesRequiredScope() throws Exception {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/v1/notifications/abc-123/status"));

    handler.handle(exchange, new AccessDeniedException("denied")).block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    JsonNode body = readBody(exchange);
    assertThat(body.get("requiredScope").asText()).isEqualTo("notification:read");
    assertThat(body.get("path").asText()).isEqualTo("/api/v1/notifications/abc-123/status");
  }

  @Test
  void deniedAuditRequest_namesDifferentRequiredScope() throws Exception {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/v1/audit"));

    handler.handle(exchange, new AccessDeniedException("denied")).block();

    JsonNode body = readBody(exchange);
    assertThat(body.get("requiredScope").asText()).isEqualTo("audit:read");
  }

  @Test
  void deniedRequest_bodyContainsOnlyTheFiveSafeKeys() throws Exception {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/v1/audit"));

    handler.handle(exchange, new AccessDeniedException("denied")).block();

    JsonNode body = readBody(exchange);
    assertThat(body.fieldNames()).toIterable().containsExactlyInAnyOrder(
        "error", "message", "requiredScope", "path", "timestamp");
  }

  @Test
  void deniedRequest_bodyLeaksNoTokenIdentityOrRealmData() throws Exception {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/v1/audit"));

    handler.handle(exchange, new AccessDeniedException("denied")).block();

    JsonNode body = readBody(exchange);
    assertThat(body.has("sub")).isFalse();
    assertThat(body.has("preferred_username")).isFalse();
    assertThat(body.has("iss")).isFalse();
    assertThat(body.has("realm")).isFalse();
  }

  @Test
  void deniedRequest_forPathMatchingNoRule_namesUnknownScope() throws Exception {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/v1/does-not-exist"));

    handler.handle(exchange, new AccessDeniedException("denied")).block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    JsonNode body = readBody(exchange);
    assertThat(body.get("requiredScope").asText()).isEqualTo("unknown");
  }

  private JsonNode readBody(MockServerWebExchange exchange) throws Exception {
    String rawBody = exchange.getResponse().getBodyAsString().block();
    return objectMapper.readTree(rawBody);
  }
}
