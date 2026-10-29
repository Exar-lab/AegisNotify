package com.aegisnotify.user.application.port.in;

import com.aegisnotify.user.application.dto.PagedResult;
import com.aegisnotify.user.domain.model.ManagedUser;
import java.util.List;

/**
 * Read-side use case exposed to {@code infrastructure.web.UserController}.
 * Gated by the {@code user:read} scope; never by {@code user:admin} alone
 * (D3, non-hierarchical scope gating).
 */
public interface QueryUsersUseCase {

  /**
   * Lists users, paginated.
   *
   * @param page zero-based page number
   * @param size page size
   * @return the requested page of users
   */
  PagedResult<ManagedUser> listUsers(int page, int size);

  /**
   * Lists users matching an optional search term, paginated.
   *
   * @param page zero-based page number
   * @param size page size
   * @param search optional search query (may be null or blank)
   * @return the requested page of users
   */
  PagedResult<ManagedUser> listUsers(int page, int size, String search);

  /**
   * Finds a single user by id.
   *
   * @param id the Keycloak user id
   * @return the matching user
   */
  ManagedUser getUser(String id);

  /**
   * Retrieves all available roles defined in the Keycloak realm.
   *
   * @return list of realm role names
   */
  List<String> getAvailableRoles();
}
