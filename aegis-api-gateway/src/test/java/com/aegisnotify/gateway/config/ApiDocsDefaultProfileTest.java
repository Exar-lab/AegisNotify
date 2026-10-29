package com.aegisnotify.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Without the {@code local} profile the gateway has no docs routes and the
 * {@code /docs/**} paths require authentication like any other path.
 */
@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class ApiDocsDefaultProfileTest {

  @Autowired
  WebTestClient webTestClient;

  @Autowired
  RouteLocator routeLocator;

  @MockBean
  ReactiveJwtDecoder jwtDecoder;

  @Test
  void docsRoutes_doNotExist() {
    assertThat(routeLocator.getRoutes().map(Route::getId).collectList().block())
        .noneMatch(id -> id.startsWith("docs-"));
  }

  @Test
  void docsPath_withoutJwt_returns401() {
    webTestClient.get().uri("/docs/notification/v3/api-docs")
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  void swaggerUiPath_withoutJwt_returns401() {
    webTestClient.get().uri("/docs/audit/swagger-ui/index.html")
        .exchange()
        .expectStatus().isUnauthorized();
  }
}
