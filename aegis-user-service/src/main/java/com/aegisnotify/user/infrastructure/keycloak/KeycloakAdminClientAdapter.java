package com.aegisnotify.user.infrastructure.keycloak;

import com.aegisnotify.user.application.dto.PagedResult;
import com.aegisnotify.user.application.port.out.UserDirectoryPort;
import com.aegisnotify.user.domain.exception.UserDirectoryUnavailableException;
import com.aegisnotify.user.domain.exception.UserNotFoundException;
import com.aegisnotify.user.domain.model.ManagedUser;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * WebClient-based {@link UserDirectoryPort} implementation calling the
 * Keycloak Admin REST API.
 *
 * <p>Read methods only in this slice (5a) — mutation methods are appended
 * to {@link UserDirectoryPort} and this adapter in Slice 5b, per the design
 * doc's "append, don't replace" note, so reverting 5b leaves this read
 * surface compiling.</p>
 *
 * <p>Every request carries a fresh {@code Authorization: Bearer} header
 * from {@link KeycloakTokenProvider} via an {@link ExchangeFilterFunction}.
 * Keycloak 404 maps to {@link UserNotFoundException}; a 401/403 from
 * Keycloak (bad service-account credentials) additionally invalidates the
 * cached token via {@link KeycloakTokenProvider#invalidate()} so the next
 * call re-authenticates. Every other {@link WebClientResponseException}
 * (5xx outages, any other 4xx) and any network-level failure both map to
 * {@link UserDirectoryUnavailableException}, which the {@code
 * GlobalExceptionHandler} turns into a generic 502 — never the caller's own
 * 403, and never carrying Keycloak's response body or a raw stack trace.</p>
 */
@Component
public class KeycloakAdminClientAdapter implements UserDirectoryPort {

  private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClientAdapter.class);
  private static final String USERS_PATH = "/admin/realms/{realm}/users";
  private static final String USER_BY_ID_PATH = "/admin/realms/{realm}/users/{id}";
  private static final String USER_COUNT_PATH = "/admin/realms/{realm}/users/count";

  private final WebClient webClient;
  private final KeycloakAdminConfig config;
  private final KeycloakTokenProvider tokenProvider;

  public KeycloakAdminClientAdapter(WebClient.Builder webClientBuilder, KeycloakAdminConfig config,
      KeycloakTokenProvider tokenProvider) {
    this.config = config;
    this.tokenProvider = tokenProvider;
    this.webClient = webClientBuilder
        .baseUrl(config.baseUrl())
        .clientConnector(new ReactorClientHttpConnector(
            HttpClient.create().responseTimeout(config.timeout())))
        .filter(bearerAuthFilter(tokenProvider))
        .build();
  }

  @Override
  public PagedResult<ManagedUser> findAll(int page, int size) {
    int first = page * size;
    try {
      List<KeycloakUserRepresentation> representations = webClient.get()
          .uri(USERS_PATH + "?first={first}&max={max}", config.realm(), first, size)
          .retrieve()
          .bodyToFlux(KeycloakUserRepresentation.class)
          .collectList()
          .block();

      Long total = webClient.get()
          .uri(USER_COUNT_PATH, config.realm())
          .retrieve()
          .bodyToMono(Long.class)
          .block();

      List<ManagedUser> content = representations == null ? List.of()
          : representations.stream().map(this::toDomain).toList();
      long totalElements = total == null ? content.size() : total;
      int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);

      return new PagedResult<>(content, page, size, totalElements, totalPages);
    } catch (WebClientResponseException.Unauthorized | WebClientResponseException.Forbidden ex) {
      tokenProvider.invalidate();
      throw unavailable(ex);
    } catch (WebClientResponseException ex) {
      throw unavailable(ex);
    } catch (WebClientRequestException ex) {
      throw unavailable(ex);
    }
  }

  @Override
  public ManagedUser findById(String id) {
    try {
      KeycloakUserRepresentation representation = webClient.get()
          .uri(USER_BY_ID_PATH, config.realm(), id)
          .retrieve()
          .bodyToMono(KeycloakUserRepresentation.class)
          .block();
      if (representation == null) {
        throw new UserNotFoundException(id);
      }
      return toDomain(representation);
    } catch (WebClientResponseException.NotFound ex) {
      throw new UserNotFoundException(id);
    } catch (WebClientResponseException.Unauthorized | WebClientResponseException.Forbidden ex) {
      tokenProvider.invalidate();
      throw unavailable(ex);
    } catch (WebClientResponseException ex) {
      throw unavailable(ex);
    } catch (WebClientRequestException ex) {
      throw unavailable(ex);
    }
  }

  private static ExchangeFilterFunction bearerAuthFilter(KeycloakTokenProvider tokenProvider) {
    return ExchangeFilterFunction.ofRequestProcessor(request -> Mono.just(
        ClientRequest.from(request)
            .headers(headers -> headers.setBearerAuth(tokenProvider.getToken()))
            .build()));
  }

  private UserDirectoryUnavailableException unavailable(Exception cause) {
    log.warn("keycloak_admin_api_unavailable reason={}", cause.getMessage());
    return new UserDirectoryUnavailableException("Keycloak Admin API is unavailable", cause);
  }

  private ManagedUser toDomain(KeycloakUserRepresentation representation) {
    Instant createdAt = representation.createdTimestamp() == null
        ? null
        : Instant.ofEpochMilli(representation.createdTimestamp());
    return new ManagedUser(
        representation.id(),
        representation.username(),
        representation.email(),
        representation.firstName(),
        representation.lastName(),
        Boolean.TRUE.equals(representation.enabled()),
        createdAt);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record KeycloakUserRepresentation(
      String id,
      String username,
      String email,
      @JsonProperty("firstName") String firstName,
      @JsonProperty("lastName") String lastName,
      Boolean enabled,
      @JsonProperty("createdTimestamp") Long createdTimestamp) {
  }
}
