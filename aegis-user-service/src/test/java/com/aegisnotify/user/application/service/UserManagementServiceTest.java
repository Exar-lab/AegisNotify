package com.aegisnotify.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.user.application.dto.NewUser;
import com.aegisnotify.user.application.dto.UserUpdate;
import com.aegisnotify.user.application.port.out.UserDirectoryPort;
import com.aegisnotify.user.domain.model.ManagedUser;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies {@link UserManagementService} delegates each mutation to {@link
 * UserDirectoryPort} without extra orchestration, and specifically that
 * disabling a user is idempotent (D4): disabling an already-disabled user
 * succeeds again rather than erroring.
 */
@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

  private static final ManagedUser ENABLED_USER = new ManagedUser(
      "u-1", "jdoe", "jdoe@example.com", "Jane", "Doe", true,
      Instant.parse("2026-01-01T00:00:00Z"));

  private static final ManagedUser DISABLED_USER = new ManagedUser(
      "u-1", "jdoe", "jdoe@example.com", "Jane", "Doe", false,
      Instant.parse("2026-01-01T00:00:00Z"));

  @Mock
  private UserDirectoryPort userDirectoryPort;

  @Test
  void createUser_delegatesToPortAndReturnsCreatedUser() {
    UserManagementService service = new UserManagementService(userDirectoryPort);
    NewUser newUser = new NewUser("jdoe", "jdoe@example.com", "Jane", "Doe");
    when(userDirectoryPort.create(newUser)).thenReturn(ENABLED_USER);

    ManagedUser result = service.createUser(newUser);

    assertThat(result).isEqualTo(ENABLED_USER);
    verify(userDirectoryPort).create(newUser);
  }

  @Test
  void updateUser_delegatesToPortAndReturnsUpdatedUser() {
    UserManagementService service = new UserManagementService(userDirectoryPort);
    UserUpdate update = new UserUpdate("jdoe@example.com", "Jane", "Doe");
    when(userDirectoryPort.update("u-1", update)).thenReturn(ENABLED_USER);

    ManagedUser result = service.updateUser("u-1", update);

    assertThat(result).isEqualTo(ENABLED_USER);
    verify(userDirectoryPort).update("u-1", update);
  }

  @Test
  void setUserEnabled_disable_setsEnabledFalse() {
    UserManagementService service = new UserManagementService(userDirectoryPort);
    when(userDirectoryPort.setEnabled("u-1", false)).thenReturn(DISABLED_USER);

    ManagedUser result = service.setUserEnabled("u-1", false);

    assertThat(result.enabled()).isFalse();
    verify(userDirectoryPort).setEnabled("u-1", false);
  }

  @Test
  void setUserEnabled_disablingAlreadyDisabledUser_isIdempotentAndSucceeds() {
    // D4: disabling an already-disabled user must succeed again (200), not
    // error. The service applies no extra guard/branching on prior
    // state — it always delegates directly to the port, and the port
    // itself is idempotent (verified separately in
    // KeycloakAdminClientAdapterTest#setEnabled_alreadyDisabled_...).
    UserManagementService service = new UserManagementService(userDirectoryPort);
    when(userDirectoryPort.setEnabled(eq("u-1"), eq(false))).thenReturn(DISABLED_USER);

    ManagedUser first = service.setUserEnabled("u-1", false);
    ManagedUser second = service.setUserEnabled("u-1", false);

    assertThat(first.enabled()).isFalse();
    assertThat(second.enabled()).isFalse();
  }

  @Test
  void resetPassword_delegatesToPort() {
    UserManagementService service = new UserManagementService(userDirectoryPort);

    service.resetPassword("u-1", "NewPassw0rd!", true);

    verify(userDirectoryPort).resetPassword("u-1", "NewPassw0rd!", true);
  }

  @Test
  void resetPassword_notTemporary_delegatesFlagUnchanged() {
    UserManagementService service = new UserManagementService(userDirectoryPort);

    service.resetPassword("u-1", "NewPassw0rd!", false);

    verify(userDirectoryPort).resetPassword(eq("u-1"), any(), eq(false));
  }
}
