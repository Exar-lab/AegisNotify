package com.aegisnotify.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * With the {@code local} profile the gateway proxies each service's API docs
 * under {@code /docs/<service>/**} (StripPrefix=2) without requiring a JWT.
 */
@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles({"test", "local"})
class ApiDocsLocalProfileTest {

  @Autowired
  WebTestClient webTestClient;

  @Autowired
  RouteLocator routeLocator;

  @MockBean
  ReactiveJwtDecoder jwtDecoder;

  private List<Route> routes() {
    return routeLocator.getRoutes().collectList().block();
  }

  private Route route(String id) {
    return routes().stream().filter(r -> r.getId().equals(id)).findFirst().orElseThrow();
  }

  @Test
  void docsRoutes_pointToTheThreeServices() {
    assertThat(route("docs-notification").getUri().toString())
        .isEqualTo("lb://aegis-notification-service");
    assertThat(route("docs-user").getUri().toString()).isEqualTo("lb://aegis-user-service");
    assertThat(route("docs-audit").getUri().toString()).isEqualTo("lb://aegis-audit-service");
  }

  @Test
  void docsRoutes_matchTheirPrefixAndStripTwoSegments() {
    Route route = route("docs-notification");
    MockServerWebExchange docs = MockServerWebExchange.from(
        MockServerHttpRequest.get("/docs/notification/v3/api-docs"));
    MockServerWebExchange other = MockServerWebExchange.from(
        MockServerHttpRequest.get("/docs/user/v3/api-docs"));

    assertThat(Mono.from(route.getPredicate().apply(docs)).block()).isTrue();
    assertThat(Mono.from(route.getPredicate().apply(other)).block()).isFalse();
    assertThat(route.getFilters()).hasSize(1);
    assertThat(route.getFilters().get(0).toString()).contains("parts = 2");
  }

  @Test
  void docsPath_withoutJwt_isNotRejectedBySecurity() {
    // No live downstream here, so the request fails at routing (5xx), never 401/403.
    webTestClient.get().uri("/docs/notification/v3/api-docs")
        .exchange()
        .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
  }

  @Test
  void swaggerUiPath_withoutJwt_isNotRejectedBySecurity() {
    webTestClient.get().uri("/docs/audit/swagger-ui/index.html")
        .exchange()
        .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
  }

  @Test
  void businessPath_withoutJwt_stillReturns401() {
    webTestClient.get().uri("/api/v1/notifications/some-id/status")
        .exchange()
        .expectStatus().isUnauthorized();
  }
}
