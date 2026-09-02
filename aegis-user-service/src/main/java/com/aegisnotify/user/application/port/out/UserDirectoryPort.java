package com.aegisnotify.user.application.port.out;

import com.aegisnotify.user.application.dto.NewUser;
import com.aegisnotify.user.application.dto.PagedResult;
import com.aegisnotify.user.application.dto.UserUpdate;
import com.aegisnotify.user.domain.model.ManagedUser;

/**
 * Vendor-neutral port for reading and managing users in the identity
 * directory. Implemented in {@code infrastructure.keycloak} by {@code
 * KeycloakAdminClientAdapter}.
 *
 * <p>Read methods landed in Slice 5a; the mutation methods below ({@code
 * create}, {@code update}, {@code setEnabled}, {@code resetPassword}) were
 * appended in Slice 5b so reverting 5b leaves the read surface intact and
 * compiling.</p>
 */
public interface UserDirectoryPort {

  /**
   * Lists users, paginated.
   *
   * @param page zero-based page number
   * @param size page size
   * @return the requested page of users
   */
  PagedResult<ManagedUser> findAll(int page, int size);

  /**
   * Finds a single user by id.
   *
   * @param id the Keycloak user id
   * @return the matching user
   */
  ManagedUser findById(String id);

  /**
   * Creates a new, enabled user.
   *
   * @param newUser the new user's profile fields
   * @return the created user, including its Keycloak-assigned id
   */
  ManagedUser create(NewUser newUser);

  /**
   * Updates an existing user's profile fields. Never changes {@code
   * enabled} state — use {@link #setEnabled(String, boolean)} for that.
   *
   * @param id the Keycloak user id
   * @param update the fields to update
   * @return the updated user
   */
  ManagedUser update(String id, UserUpdate update);

  /**
   * Sets a user's enabled/disabled state. This is the ONLY lifecycle
   * mutation exposed by this port — there is no delete (D4). Idempotent:
   * setting the same state twice succeeds both times.
   *
   * @param id the Keycloak user id
   * @param enabled the desired enabled state
   * @return the updated user
   */
  ManagedUser setEnabled(String id, boolean enabled);

  /**
   * Resets a user's password.
   *
   * @param id the Keycloak user id
   * @param newPassword the new password value
   * @param temporary whether Keycloak should force the user to change this
   *     password on next login
   */
  void resetPassword(String id, String newPassword, boolean temporary);
}
