package com.aegisnotify.user.infrastructure.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.aegisnotify.user.application.dto.NewUser;
import com.aegisnotify.user.application.dto.PagedResult;
import com.aegisnotify.user.application.dto.UserUpdate;
import com.aegisnotify.user.domain.exception.UserAlreadyExistsException;
import com.aegisnotify.user.domain.exception.UserDirectoryUnavailableException;
import com.aegisnotify.user.domain.exception.UserNotFoundException;
import com.aegisnotify.user.domain.model.ManagedUser;
import java.io.IOException;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

/**
 * Verifies {@link KeycloakAdminClientAdapter}'s status-code error mapping
 * (404 -&gt; not-found, 401/403 -&gt; unavailable) and successful mapping to
 * {@link ManagedUser}, against a real HTTP server (MockWebServer). The
 * {@link KeycloakTokenProvider} collaborator is mocked so tests exercise the
 * adapter's own HTTP/error-mapping logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class KeycloakAdminClientAdapterTest {

  private MockWebServer server;
  private KeycloakAdminClientAdapter adapter;

  @Mock
  private KeycloakTokenProvider tokenProvider;

  @BeforeEach
  void startServer() throws IOException {
    server = new MockWebServer();
    server.start();
    lenient().when(tokenProvider.getToken()).thenReturn("test-token");

    KeycloakAdminConfig config = new KeycloakAdminConfig(
        "http://localhost:" + server.getPort(), "aegis", "aegis-user-service", "secret",
        Duration.ofSeconds(5));
    adapter = new KeycloakAdminClientAdapter(WebClient.builder(), config, tokenProvider);
  }

  @AfterEach
  void stopServer() throws IOException {
    server.shutdown();
  }

  @Test
  void findById_userExists_returnsManagedUser() {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"id\":\"u-1\",\"username\":\"jdoe\",\"email\":\"jdoe@example.com\","
            + "\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"enabled\":true,"
            + "\"createdTimestamp\":1700000000000}"));

    ManagedUser user = adapter.findById("u-1");

    assertThat(user.id()).isEqualTo("u-1");
    assertThat(user.username()).isEqualTo("jdoe");
    assertThat(user.email()).isEqualTo("jdoe@example.com");
    assertThat(user.firstName()).isEqualTo("Jane");
    assertThat(user.lastName()).isEqualTo("Doe");
    assertThat(user.enabled()).isTrue();
  }

  @Test
  void findById_notFound_throwsUserNotFoundException() {
    server.enqueue(new MockResponse().setResponseCode(404));

    assertThatThrownBy(() -> adapter.findById("missing"))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void findById_unauthorized_throwsUserDirectoryUnavailableException() {
    server.enqueue(new MockResponse().setResponseCode(401));

    assertThatThrownBy(() -> adapter.findById("u-1"))
        .isInstanceOf(UserDirectoryUnavailableException.class);
  }

  @Test
  void findById_forbidden_throwsUserDirectoryUnavailableException() {
    server.enqueue(new MockResponse().setResponseCode(403));

    assertThatThrownBy(() -> adapter.findById("u-1"))
        .isInstanceOf(UserDirectoryUnavailableException.class);
  }

  @Test
  void findAll_success_returnsPagedResultOfManagedUsers() {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("[{\"id\":\"u-1\",\"username\":\"jdoe\",\"email\":\"jdoe@example.com\","
            + "\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"enabled\":true,"
            + "\"createdTimestamp\":1700000000000}]"));
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("1"));

    PagedResult<ManagedUser> result = adapter.findAll(0, 20);

    assertThat(result.content()).hasSize(1);
    assertThat(result.content().get(0).username()).isEqualTo("jdoe");
    assertThat(result.page()).isZero();
    assertThat(result.size()).isEqualTo(20);
    assertThat(result.totalElements()).isEqualTo(1L);
    assertThat(result.totalPages()).isEqualTo(1);
  }

  @Test
  void findAll_unauthorized_throwsUserDirectoryUnavailableException() {
    server.enqueue(new MockResponse().setResponseCode(401));

    assertThatThrownBy(() -> adapter.findAll(0, 20))
        .isInstanceOf(UserDirectoryUnavailableException.class);
  }

  @Test
  void findById_serverError_throwsUserDirectoryUnavailableException() {
    server.enqueue(new MockResponse().setResponseCode(500));

    assertThatThrownBy(() -> adapter.findById("u-1"))
        .isInstanceOf(UserDirectoryUnavailableException.class);
  }

  @Test
  void findAll_serviceUnavailable_throwsUserDirectoryUnavailableException() {
    server.enqueue(new MockResponse().setResponseCode(503));

    assertThatThrownBy(() -> adapter.findAll(0, 20))
        .isInstanceOf(UserDirectoryUnavailableException.class);
  }

  @Test
  void findById_unauthorized_invalidatesCachedToken() {
    server.enqueue(new MockResponse().setResponseCode(401));

    assertThatThrownBy(() -> adapter.findById("u-1"))
        .isInstanceOf(UserDirectoryUnavailableException.class);

    verify(tokenProvider).invalidate();
  }

  @Test
  void findAll_forbidden_invalidatesCachedToken() {
    server.enqueue(new MockResponse().setResponseCode(403));

    assertThatThrownBy(() -> adapter.findAll(0, 20))
        .isInstanceOf(UserDirectoryUnavailableException.class);

    verify(tokenProvider).invalidate();
  }

  @Test
  void findById_serverNeverResponds_boundedByConfiguredTimeoutInsteadOfHangingForever() {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
    KeycloakAdminConfig shortTimeoutConfig = new KeycloakAdminConfig(
        "http://localhost:" + server.getPort(), "aegis", "aegis-user-service", "secret",
        Duration.ofMillis(200));
    KeycloakAdminClientAdapter shortTimeoutAdapter =
        new KeycloakAdminClientAdapter(WebClient.builder(), shortTimeoutConfig, tokenProvider);

    assertThatThrownBy(() -> shortTimeoutAdapter.findById("u-1"))
        .isInstanceOf(UserDirectoryUnavailableException.class)
        .hasCauseInstanceOf(WebClientRequestException.class);
  }

  // --- Slice 5b: mutation methods ---

  @Test
  void create_success_returnsCreatedUserWithIdParsedFromLocationHeader() {
    server.enqueue(new MockResponse()
        .setResponseCode(201)
        .addHeader("Location",
            "http://localhost:" + server.getPort() + "/admin/realms/aegis/users/u-new"));
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"id\":\"u-new\",\"username\":\"newuser\",\"email\":\"newuser@example.com\","
            + "\"firstName\":\"New\",\"lastName\":\"User\",\"enabled\":true,"
            + "\"createdTimestamp\":1700000000000}"));

    ManagedUser created = adapter.create(
        new NewUser("newuser", "newuser@example.com", "New", "User"));

    assertThat(created.id()).isEqualTo("u-new");
    assertThat(created.username()).isEqualTo("newuser");
    assertThat(created.enabled()).isTrue();
  }

  @Test
  void create_conflict_throwsUserAlreadyExistsException() {
    server.enqueue(new MockResponse().setResponseCode(409));

    assertThatThrownBy(
        () -> adapter.create(new NewUser("dup", "dup@example.com", "Dup", "User")))
        .isInstanceOf(UserAlreadyExistsException.class);
  }

  @Test
  void create_missingLocationHeader_throwsUserDirectoryUnavailableException() {
    server.enqueue(new MockResponse().setResponseCode(201));

    assertThatThrownBy(
        () -> adapter.create(new NewUser("newuser", "newuser@example.com", "New", "User")))
        .isInstanceOf(UserDirectoryUnavailableException.class);
  }

  @Test
  void create_unauthorized_throwsUserDirectoryUnavailableExceptionAndInvalidatesToken() {
    server.enqueue(new MockResponse().setResponseCode(401));

    assertThatThrownBy(
        () -> adapter.create(new NewUser("newuser", "newuser@example.com", "New", "User")))
        .isInstanceOf(UserDirectoryUnavailableException.class);

    verify(tokenProvider).invalidate();
  }

  @Test
  void update_success_returnsUpdatedUser() {
    server.enqueue(new MockResponse().setResponseCode(204));
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"id\":\"u-1\",\"username\":\"jdoe\",\"email\":\"updated@example.com\","
            + "\"firstName\":\"Janet\",\"lastName\":\"Doe\",\"enabled\":true,"
            + "\"createdTimestamp\":1700000000000}"));

    ManagedUser updated = adapter.update(
        "u-1", new UserUpdate("updated@example.com", "Janet", "Doe"));

    assertThat(updated.email()).isEqualTo("updated@example.com");
    assertThat(updated.firstName()).isEqualTo("Janet");
  }

  @Test
  void update_notFound_throwsUserNotFoundException() {
    server.enqueue(new MockResponse().setResponseCode(404));

    assertThatThrownBy(
        () -> adapter.update("missing", new UserUpdate("e@example.com", "F", "L")))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void setEnabled_disable_returnsUserWithEnabledFalse() {
    server.enqueue(new MockResponse().setResponseCode(204));
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"id\":\"u-1\",\"username\":\"jdoe\",\"email\":\"jdoe@example.com\","
            + "\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"enabled\":false,"
            + "\"createdTimestamp\":1700000000000}"));

    ManagedUser result = adapter.setEnabled("u-1", false);

    assertThat(result.enabled()).isFalse();
  }

  @Test
  void setEnabled_alreadyDisabled_idempotentSucceedsAgain() {
    // Idempotent disable: Keycloak's PUT succeeds (204) even when the user
    // is already enabled=false; no special-case handling needed here.
    server.enqueue(new MockResponse().setResponseCode(204));
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"id\":\"u-1\",\"username\":\"jdoe\",\"email\":\"jdoe@example.com\","
            + "\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"enabled\":false,"
            + "\"createdTimestamp\":1700000000000}"));

    ManagedUser result = adapter.setEnabled("u-1", false);

    assertThat(result.enabled()).isFalse();
  }

  @Test
  void setEnabled_notFound_throwsUserNotFoundException() {
    server.enqueue(new MockResponse().setResponseCode(404));

    assertThatThrownBy(() -> adapter.setEnabled("missing", false))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void resetPassword_success_completesWithoutError() {
    server.enqueue(new MockResponse().setResponseCode(204));

    adapter.resetPassword("u-1", "NewPassw0rd!", true);

    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void resetPassword_notFound_throwsUserNotFoundException() {
    server.enqueue(new MockResponse().setResponseCode(404));

    assertThatThrownBy(() -> adapter.resetPassword("missing", "NewPassw0rd!", true))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void resetPassword_forbidden_throwsUserDirectoryUnavailableExceptionAndInvalidatesToken() {
    server.enqueue(new MockResponse().setResponseCode(403));

    assertThatThrownBy(() -> adapter.resetPassword("u-1", "NewPassw0rd!", true))
        .isInstanceOf(UserDirectoryUnavailableException.class);

    verify(tokenProvider).invalidate();
  }
}
