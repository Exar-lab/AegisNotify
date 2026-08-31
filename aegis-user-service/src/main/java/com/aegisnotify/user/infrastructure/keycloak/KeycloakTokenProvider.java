package com.aegisnotify.user.infrastructure.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

/**
 * Acquires and caches a Keycloak client-credentials access token for the
 * service-account client configured by {@link KeycloakAdminConfig}.
 *
 * <p>The token is cached in an {@link AtomicReference} until {@code
 * expires_in} minus a 30-second skew elapses, so callers avoid a token
 * round-trip on every Admin REST call. Token-endpoint requests that fail
 * with 401 are retried exactly once (Keycloak's own auth stack has been
 * observed to flap briefly right after startup); a second consecutive 401
 * propagates as a {@link WebClientResponseException}. Every request is
 * bounded by {@link KeycloakAdminConfig#timeout()} so a hung or slow
 * Keycloak can never block the calling thread indefinitely.</p>
 */
@Component
public class KeycloakTokenProvider {

  private static final Logger log = LoggerFactory.getLogger(KeycloakTokenProvider.class);
  private static final Duration EXPIRY_SKEW = Duration.ofSeconds(30);
  private static final String TOKEN_PATH = "/realms/{realm}/protocol/openid-connect/token";

  private final WebClient webClient;
  private final KeycloakAdminConfig config;
  private final Clock clock;
  private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

  public KeycloakTokenProvider(WebClient.Builder webClientBuilder, KeycloakAdminConfig config) {
    this(webClientBuilder
        .baseUrl(config.baseUrl())
        .clientConnector(new ReactorClientHttpConnector(
            HttpClient.create().responseTimeout(config.timeout())))
        .build(), config, Clock.systemUTC());
  }

  KeycloakTokenProvider(WebClient webClient, KeycloakAdminConfig config, Clock clock) {
    this.webClient = webClient;
    this.config = config;
    this.clock = clock;
  }

  /**
   * Returns a currently-valid access token, reusing the cached value while
   * it has at least {@link #EXPIRY_SKEW} left before expiry.
   *
   * @return a bearer access token, without the {@code Bearer } prefix
   */
  public synchronized String getToken() {
    CachedToken current = cachedToken.get();
    if (current != null && current.isValid(clock, EXPIRY_SKEW)) {
      return current.value();
    }
    CachedToken refreshed = fetchToken();
    cachedToken.set(refreshed);
    return refreshed.value();
  }

  /**
   * Discards the cached token so the next {@link #getToken()} call fetches
   * a fresh one. Intended for callers that detect a 401 from a downstream
   * Admin REST call and want to force a refresh before retrying.
   */
  public synchronized void invalidate() {
    cachedToken.set(null);
  }

  private CachedToken fetchToken() {
    try {
      return requestToken();
    } catch (WebClientResponseException.Unauthorized ex) {
      log.warn("keycloak_token_request_unauthorized_retrying_once");
      return requestToken();
    }
  }

  private CachedToken requestToken() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", config.clientId());
    form.add("client_secret", config.clientSecret());

    TokenResponse response = webClient.post()
        .uri(TOKEN_PATH, config.realm())
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .bodyValue(form)
        .retrieve()
        .bodyToMono(TokenResponse.class)
        .block();

    Instant expiresAt = Instant.now(clock).plusSeconds(response.expiresIn());
    return new CachedToken(response.accessToken(), expiresAt);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") long expiresIn) {
  }

  private record CachedToken(String value, Instant expiresAt) {

    boolean isValid(Clock clock, Duration skew) {
      return Instant.now(clock).isBefore(expiresAt.minus(skew));
    }
  }
}
