package com.aegisnotify.user.infrastructure.keycloak;

import com.aegisnotify.user.application.dto.NewUser;
import com.aegisnotify.user.application.dto.PagedResult;
import com.aegisnotify.user.application.dto.UserUpdate;
import com.aegisnotify.user.application.port.out.UserDirectoryPort;
import com.aegisnotify.user.domain.exception.RoleNotFoundException;
import com.aegisnotify.user.domain.exception.UserAlreadyExistsException;
import com.aegisnotify.user.domain.exception.UserDirectoryUnavailableException;
import com.aegisnotify.user.domain.exception.UserNotFoundException;
import com.aegisnotify.user.domain.model.ManagedUser;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * WebClient-based {@link UserDirectoryPort} implementation calling the
 * Keycloak Admin REST API.
 *
 * <p>Read methods landed in Slice 5a; the mutation methods below were
 * appended in Slice 5b per the design doc's "append, don't replace" note,
 * so reverting 5b leaves this read surface compiling.</p>
 *
 * <p>Every request carries a fresh {@code Authorization: Bearer} header
 * from {@link KeycloakTokenProvider} via an {@link ExchangeFilterFunction}.
 * Keycloak 404 maps to {@link UserNotFoundException}; a Keycloak 409 on
 * create maps to {@link UserAlreadyExistsException}; a 401/403 from
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
  private static final String RESET_PASSWORD_PATH =
      "/admin/realms/{realm}/users/{id}/reset-password";
  private static final String USER_ROLES_PATH =
      "/admin/realms/{realm}/users/{id}/role-mappings/realm";
  private static final String REALM_ROLES_PATH = "/admin/realms/{realm}/roles";

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
    return findAll(page, size, null);
  }

  @Override
  public PagedResult<ManagedUser> findAll(int page, int size, String search) {
    int first = page * size;
    try {
      boolean hasSearch = search != null && !search.isBlank();
      String listUri = hasSearch
          ? USERS_PATH + "?first={first}&max={max}&search={search}"
          : USERS_PATH + "?first={first}&max={max}";
      String countUri = hasSearch
          ? USER_COUNT_PATH + "?search={search}"
          : USER_COUNT_PATH;

      List<KeycloakUserRepresentation> representations = hasSearch
          ? webClient.get()
              .uri(listUri, config.realm(), first, size, search.trim())
              .retrieve()
              .bodyToFlux(KeycloakUserRepresentation.class)
              .collectList()
              .block()
          : webClient.get()
              .uri(listUri, config.realm(), first, size)
              .retrieve()
              .bodyToFlux(KeycloakUserRepresentation.class)
              .collectList()
              .block();

      Long total = hasSearch
          ? webClient.get()
              .uri(countUri, config.realm(), search.trim())
              .retrieve()
              .bodyToMono(Long.class)
              .block()
          : webClient.get()
              .uri(countUri, config.realm())
              .retrieve()
              .bodyToMono(Long.class)
              .block();

      List<KeycloakUserRepresentation> users =
          representations == null ? List.of() : representations;

      List<ManagedUser> content = Flux.fromIterable(users)
          .flatMap(user -> fetchRoles(user.id()).map(roles -> toDomain(user, roles)), 5)
          .collectList()
          .block();

      if (content == null) {
        content = List.of();
      }
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
      List<KeycloakRoleRepresentation> roleReps = webClient.get()
          .uri(USER_ROLES_PATH, config.realm(), id)
          .retrieve()
          .bodyToFlux(KeycloakRoleRepresentation.class)
          .collectList()
          .block();
      List<String> roles = roleReps == null
          ? List.of()
          : roleReps.stream().map(KeycloakRoleRepresentation::name).toList();

      return toDomain(representation, roles);
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

  @Override
  public List<String> getAvailableRoles() {
    try {
      List<KeycloakRoleRepresentation> roles = fetchRealmRoles();
      return roles.stream().map(KeycloakRoleRepresentation::name).toList();
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
  public ManagedUser create(NewUser newUser) {
    try {
      KeycloakUserRepresentation representation = new KeycloakUserRepresentation(
          null, newUser.username(), newUser.email(), newUser.firstName(), newUser.lastName(),
          true, null);

      ResponseEntity<Void> response = webClient.post()
          .uri(USERS_PATH, config.realm())
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(representation)
          .retrieve()
          .toBodilessEntity()
          .block();

      String id;
      try {
        id = extractId(response);
      } catch (IllegalStateException ex) {
        throw unavailable(ex);
      }
      return findById(id);
    } catch (WebClientResponseException.Conflict ex) {
      throw new UserAlreadyExistsException(newUser.username());
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
  public ManagedUser update(String id, UserUpdate update) {
    try {
      KeycloakUserRepresentation representation = new KeycloakUserRepresentation(
          null, null, update.email(), update.firstName(), update.lastName(), null, null);

      webClient.put()
          .uri(USER_BY_ID_PATH, config.realm(), id)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(representation)
          .retrieve()
          .toBodilessEntity()
          .block();

      return findById(id);
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

  @Override
  public ManagedUser setEnabled(String id, boolean enabled) {
    try {
      KeycloakUserRepresentation representation = new KeycloakUserRepresentation(
          null, null, null, null, null, enabled, null);

      webClient.put()
          .uri(USER_BY_ID_PATH, config.realm(), id)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(representation)
          .retrieve()
          .toBodilessEntity()
          .block();

      return findById(id);
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

  @Override
  public ManagedUser setRoles(String id, List<String> roles) {
    List<String> normalizedRoles = roles == null
        ? List.of()
        : roles.stream()
            .filter(r -> r != null && !r.isBlank())
            .map(String::trim)
            .distinct()
            .toList();

    try {
      List<KeycloakRoleRepresentation> availableRoles = fetchRealmRoles();
      Map<String, KeycloakRoleRepresentation> realmRolesByName = availableRoles.stream()
          .collect(Collectors.toMap(KeycloakRoleRepresentation::name, r -> r, (a, b) -> a));

      for (String requestedRole : normalizedRoles) {
        if (!realmRolesByName.containsKey(requestedRole)) {
          throw new RoleNotFoundException(requestedRole);
        }
      }

      List<KeycloakRoleRepresentation> currentRoleMappings = fetchUserRoleMappings(id);
      Set<String> targetRoleNames = new HashSet<>(normalizedRoles);

      List<KeycloakRoleRepresentation> toDelete = currentRoleMappings.stream()
          .filter(r -> !targetRoleNames.contains(r.name()))
          .toList();

      Set<String> currentRoleNames = currentRoleMappings.stream()
          .map(KeycloakRoleRepresentation::name)
          .collect(Collectors.toSet());

      List<KeycloakRoleRepresentation> toAdd = normalizedRoles.stream()
          .filter(r -> !currentRoleNames.contains(r))
          .map(realmRolesByName::get)
          .toList();

      if (!toDelete.isEmpty()) {
        webClient.method(HttpMethod.DELETE)
            .uri(USER_ROLES_PATH, config.realm(), id)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(toDelete)
            .retrieve()
            .toBodilessEntity()
            .block();
      }

      if (!toAdd.isEmpty()) {
        webClient.post()
            .uri(USER_ROLES_PATH, config.realm(), id)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(toAdd)
            .retrieve()
            .toBodilessEntity()
            .block();
      }

      return findById(id);
    } catch (RoleNotFoundException ex) {
      throw ex;
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

  @Override
  public void resetPassword(String id, String newPassword, boolean temporary) {
    try {
      webClient.put()
          .uri(RESET_PASSWORD_PATH, config.realm(), id)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new KeycloakCredentialRepresentation("password", newPassword, temporary))
          .retrieve()
          .toBodilessEntity()
          .block();
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

  private Mono<List<String>> fetchRoles(String userId) {
    return webClient.get()
        .uri(USER_ROLES_PATH, config.realm(), userId)
        .retrieve()
        .bodyToFlux(KeycloakRoleRepresentation.class)
        .map(KeycloakRoleRepresentation::name)
        .collectList()
        .defaultIfEmpty(List.of());
  }

  private List<KeycloakRoleRepresentation> fetchRealmRoles() {
    List<KeycloakRoleRepresentation> roles = webClient.get()
        .uri(REALM_ROLES_PATH, config.realm())
        .retrieve()
        .bodyToFlux(KeycloakRoleRepresentation.class)
        .collectList()
        .block();
    return roles == null ? List.of() : roles;
  }

  private List<KeycloakRoleRepresentation> fetchUserRoleMappings(String userId) {
    List<KeycloakRoleRepresentation> roles = webClient.get()
        .uri(USER_ROLES_PATH, config.realm(), userId)
        .retrieve()
        .bodyToFlux(KeycloakRoleRepresentation.class)
        .collectList()
        .block();
    return roles == null ? List.of() : roles;
  }

  private static String extractId(ResponseEntity<Void> response) {
    String location = response == null
        ? null
        : response.getHeaders().getFirst(HttpHeaders.LOCATION);
    if (location == null) {
      throw new IllegalStateException("Keycloak create-user response carried no Location header");
    }
    return location.substring(location.lastIndexOf('/') + 1);
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

  private ManagedUser toDomain(KeycloakUserRepresentation representation, List<String> roles) {
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
        createdAt,
        roles);
  }

  private ManagedUser toDomain(KeycloakUserRepresentation representation) {
    return toDomain(representation, List.of());
  }

  // NON_NULL: outbound create/update/setEnabled payloads only set the
  // fields they intend to change; Keycloak preserves any field omitted
  // from the JSON body rather than clearing it, so nulls must never be
  // serialized. Inbound (GET) responses always populate every field, so
  // this has no effect on read-path deserialization.
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record KeycloakUserRepresentation(
      String id,
      String username,
      String email,
      @JsonProperty("firstName") String firstName,
      @JsonProperty("lastName") String lastName,
      Boolean enabled,
      @JsonProperty("createdTimestamp") Long createdTimestamp) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record KeycloakRoleRepresentation(String id, String name, String description) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record KeycloakCredentialRepresentation(String type, String value, boolean temporary) {
  }
}
