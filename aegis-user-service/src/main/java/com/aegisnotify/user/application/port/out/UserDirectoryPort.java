package com.aegisnotify.user.application.port.out;

import com.aegisnotify.user.application.dto.PagedResult;
import com.aegisnotify.user.domain.model.ManagedUser;

/**
 * Vendor-neutral port for reading managed users from the identity
 * directory. Implemented in {@code infrastructure.keycloak} by {@code
 * KeycloakAdminClientAdapter}.
 *
 * <p>Read-only in Slice 5a. Slice 5b appends mutation methods ({@code
 * create}, {@code update}, {@code setEnabled}, {@code resetPassword}) to
 * this same interface so reverting 5b leaves this read surface intact.</p>
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
}
