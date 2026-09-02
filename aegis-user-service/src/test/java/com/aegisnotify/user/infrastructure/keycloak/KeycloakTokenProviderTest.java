package com.aegisnotify.user.infrastructure.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Verifies {@link KeycloakTokenProvider}'s caching, expiry-skew refresh, and
 * single-retry-on-401 behavior against a real HTTP server (MockWebServer),
 * matching this service's WebClient-based adapter conventions.
 */
class KeycloakTokenProviderTest {

  private MockWebServer server;
  private KeycloakAdminConfig config;

  @BeforeEach
  void startServer() throws IOException {
    server = new MockWebServer();
    server.start();
    config = new KeycloakAdminConfig(
        "http://localhost:" + server.getPort(), "aegis", "aegis-user-service", "secret",
        Duration.ofSeconds(5));
  }

  @AfterEach
  void stopServer() throws IOException {
    server.shutdown();
  }

  private KeycloakTokenProvider provider(Clock clock) {
    WebClient webClient = WebClient.builder()
        .baseUrl("http://localhost:" + server.getPort())
        .build();
    return new KeycloakTokenProvider(webClient, config, clock);
  }

  @Test
  void getToken_firstCall_fetchesToken() {
    server.enqueue(jsonTokenResponse("token-1", 300));

    String token = provider(fixedClock()).getToken();

    assertThat(token).isEqualTo("token-1");
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void getToken_withinExpirySkewWindow_returnsCachedTokenWithoutNewRequest() {
    server.enqueue(jsonTokenResponse("token-1", 300));
    KeycloakTokenProvider provider = provider(fixedClock());

    provider.getToken();
    String second = provider.getToken();

    assertThat(second).isEqualTo("token-1");
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void getToken_afterExpiryMinusSkewElapsed_refreshesToken() {
    server.enqueue(jsonTokenResponse("token-1", 60));
    server.enqueue(jsonTokenResponse("token-2", 300));

    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    KeycloakTokenProvider provider = provider(clock);

    String first = provider.getToken();
    // expires_in=60s, skew=30s -> cached token is stale after 30s elapsed.
    clock.advance(Duration.ofSeconds(31));
    String second = provider.getToken();

    assertThat(first).isEqualTo("token-1");
    assertThat(second).isEqualTo("token-2");
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void getToken_serverReturns401Once_retriesAndSucceeds() {
    server.enqueue(new MockResponse().setResponseCode(401));
    server.enqueue(jsonTokenResponse("token-recovered", 300));

    String token = provider(fixedClock()).getToken();

    assertThat(token).isEqualTo("token-recovered");
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void getToken_serverReturns401Twice_propagatesFailureAfterOneRetry() {
    server.enqueue(new MockResponse().setResponseCode(401));
    server.enqueue(new MockResponse().setResponseCode(401));

    assertThatThrownBy(() -> provider(fixedClock()).getToken())
        .isInstanceOf(WebClientResponseException.class);
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void getToken_serverNeverResponds_boundedByConfiguredTimeoutInsteadOfHangingForever() {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
    KeycloakAdminConfig shortTimeoutConfig = new KeycloakAdminConfig(
        "http://localhost:" + server.getPort(), "aegis", "aegis-user-service", "secret",
        Duration.ofMillis(200));
    KeycloakTokenProvider provider =
        new KeycloakTokenProvider(WebClient.builder(), shortTimeoutConfig);

    assertThatThrownBy(provider::getToken).isInstanceOf(WebClientRequestException.class);
  }

  private static Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  }

  private static MockResponse jsonTokenResponse(String accessToken, long expiresInSeconds) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            "{\"access_token\":\"" + accessToken + "\",\"expires_in\":" + expiresInSeconds + "}");
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
