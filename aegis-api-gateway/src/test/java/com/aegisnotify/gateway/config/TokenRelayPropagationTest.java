package com.aegisnotify.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Proves the {@code audit-read} route actually relays the caller's bearer token downstream
 * (spec requirement "JWT Propagation to Audit Service", scenario "Bearer token is relayed on
 * proxied audit requests"), rather than trusting static config review alone.
 *
 * <p>{@code mockJwt()} (used elsewhere in this suite) injects the {@code Authentication}
 * directly into the reactive security context and never puts a real {@code Authorization}
 * header on the wire, so it cannot prove header propagation. This test instead sends a real
 * {@code Authorization: Bearer <token>} header, stubs {@link ReactiveJwtDecoder} to accept it,
 * and adds a dedicated probe route pointed at a local in-process HTTP server.</p>
 *
 * <p>The probe route uses its own path ({@code /api/v1/relay-probe}) rather than overriding the
 * shared {@code audit-read} route in place: Spring Boot's relaxed binder resolves the whole
 * {@code spring.cloud.gateway.routes} list from a single already-populated property source once
 * {@code application-test.yml} defines it, so a property-based partial override of one existing
 * element (or an appended new element) is silently left unbound.</p>
 *
 * <p><b>Finding</b>: {@code TokenRelay=} was previously a global default-filter in
 * {@code application.yml} and has since been removed as dead config — it resolved to a no-op
 * pass-through for this gateway. {@code TokenRelayGatewayFilterFactory} only relays a token it
 * fetches itself from a {@code ReactiveOAuth2AuthorizedClientManager}, which it can only do for
 * an {@code OAuth2AuthenticationToken} (an {@code oauth2Login} client principal). This gateway
 * authenticates callers as a resource server ({@code JwtAuthenticationToken}), for which the
 * filter's authorization-request lookup is always empty, so it falls through to
 * {@code defaultIfEmpty(exchange)} unchanged. This test still explicitly re-adds
 * {@code TokenRelay=} via {@code @SpringBootTest(properties = ...)} on the probe route, purely
 * to characterize that behavior as a regression guard: even if the filter is ever reintroduced,
 * the header this test observes downstream is the original client {@code Authorization} header,
 * forwarded by Spring Cloud Gateway's normal proxying — not an active token exchange performed
 * by {@code TokenRelay=} itself.</p>
 */
@SpringBootTest(properties = {
    "spring.cloud.gateway.default-filters[0]=TokenRelay="
})
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class TokenRelayPropagationTest {

  private static final String RAW_TOKEN = "relay-test-token";
  private static final int PROBE_PORT = 8090;
  private static final AtomicReference<String> RECEIVED_AUTH_HEADER = new AtomicReference<>();
  private static final HttpServer DOWNSTREAM = startDownstream();

  @Autowired
  WebTestClient webTestClient;

  @MockBean
  ReactiveJwtDecoder jwtDecoder;

  @AfterAll
  static void stopDownstream() {
    DOWNSTREAM.stop(0);
  }

  @Test
  void bearerTokenIsRelayedToAuditService() {
    Jwt jwt = Jwt.withTokenValue(RAW_TOKEN)
        .header("alg", "none")
        .claim("sub", "audit-user")
        .claim("scope", "audit:read")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .build();
    Mockito.when(jwtDecoder.decode(RAW_TOKEN)).thenReturn(Mono.just(jwt));

    webTestClient.get().uri("/api/v1/relay-probe")
        .header("Authorization", "Bearer " + RAW_TOKEN)
        .exchange()
        .expectStatus().isOk();

    assertThat(RECEIVED_AUTH_HEADER.get()).isEqualTo("Bearer " + RAW_TOKEN);
  }

  private static HttpServer startDownstream() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", PROBE_PORT), 0);
      server.createContext("/", exchange -> {
        RECEIVED_AUTH_HEADER.set(exchange.getRequestHeaders().getFirst("Authorization"));
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().close();
      });
      server.setExecutor(null);
      server.start();
      return server;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
