package com.aegisnotify.user.application.port.in;

import com.aegisnotify.user.application.dto.NewUser;
import com.aegisnotify.user.application.dto.UserUpdate;
import com.aegisnotify.user.domain.model.ManagedUser;

/**
 * Write-side use case exposed to {@code infrastructure.web.UserController}.
 * Gated by the {@code user:admin} scope; never by {@code user:read} alone
 * (D3, non-hierarchical scope gating).
 *
 * <p>There is no delete operation here or anywhere else in this module
 * (D4) — {@link #setUserEnabled(String, boolean)} is the only lifecycle
 * mutation, and it is idempotent: disabling an already-disabled user
 * succeeds again rather than erroring.</p>
 */
public interface ManageUsersUseCase {

  /**
   * Creates a new, enabled user.
   *
   * @param newUser the new user's profile fields
   * @return the created user
   */
  ManagedUser createUser(NewUser newUser);

  /**
   * Updates an existing user's profile fields.
   *
   * @param id the Keycloak user id
   * @param update the fields to update
   * @return the updated user
   */
  ManagedUser updateUser(String id, UserUpdate update);

  /**
   * Enables or disables a user. Idempotent.
   *
   * @param id the Keycloak user id
   * @param enabled the desired enabled state
   * @return the updated user
   */
  ManagedUser setUserEnabled(String id, boolean enabled);

  /**
   * Resets a user's password.
   *
   * @param id the Keycloak user id
   * @param newPassword the new password value
   * @param temporary whether the user must change this password on next
   *     login
   */
  void resetPassword(String id, String newPassword, boolean temporary);
}
