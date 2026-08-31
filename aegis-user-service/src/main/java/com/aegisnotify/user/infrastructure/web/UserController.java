package com.aegisnotify.user.infrastructure.web;

import com.aegisnotify.user.application.dto.PagedResult;
import com.aegisnotify.user.application.port.in.QueryUsersUseCase;
import com.aegisnotify.user.domain.model.ManagedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for reading managed users.
 *
 * <p>Read-only in this slice — no create/update/disable/reset-password
 * routes exist yet (Slice 5b), and no delete route is ever added (D4).
 * Every method mapped here is gated by {@code user:read} in {@code
 * SecurityConfig}.</p>
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final QueryUsersUseCase queryUsersUseCase;

  public UserController(QueryUsersUseCase queryUsersUseCase) {
    this.queryUsersUseCase = queryUsersUseCase;
  }

  /**
   * Lists users, paginated.
   *
   * @param page zero-based page number (default 0)
   * @param size page size (default 20)
   * @return paginated managed users
   */
  @GetMapping
  public ResponseEntity<PagedResult<ManagedUser>> listUsers(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(queryUsersUseCase.listUsers(page, size));
  }

  /**
   * Returns a single user by id.
   *
   * @param id the Keycloak user id
   * @return the matching user
   */
  @GetMapping("/{id}")
  public ResponseEntity<ManagedUser> getUser(@PathVariable String id) {
    return ResponseEntity.ok(queryUsersUseCase.getUser(id));
  }
}
