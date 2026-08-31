package com.aegisnotify.user.infrastructure.web;

import com.aegisnotify.user.application.dto.PagedResult;
import com.aegisnotify.user.application.port.in.ManageUsersUseCase;
import com.aegisnotify.user.application.port.in.QueryUsersUseCase;
import com.aegisnotify.user.domain.model.ManagedUser;
import com.aegisnotify.user.infrastructure.web.dto.CreateUserRequest;
import com.aegisnotify.user.infrastructure.web.dto.ResetPasswordRequest;
import com.aegisnotify.user.infrastructure.web.dto.SetUserStatusRequest;
import com.aegisnotify.user.infrastructure.web.dto.UpdateUserRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for reading and managing users.
 *
 * <p>Read endpoints (GET) are gated by {@code user:read}; mutation
 * endpoints (POST/PUT/PATCH) are gated by {@code user:admin} — the two
 * scopes are non-hierarchical (D3), enforced in {@code SecurityConfig}.
 * There is no delete endpoint here or anywhere in this module, and never
 * will be (D4); disabling a user via {@link #setStatus} is the only
 * lifecycle mutation, and it is idempotent.</p>
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final QueryUsersUseCase queryUsersUseCase;
  private final ManageUsersUseCase manageUsersUseCase;

  public UserController(QueryUsersUseCase queryUsersUseCase,
      ManageUsersUseCase manageUsersUseCase) {
    this.queryUsersUseCase = queryUsersUseCase;
    this.manageUsersUseCase = manageUsersUseCase;
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
   * Returns a single user by id. Disabled users remain fully visible here
   * (D4) — disable is not delete.
   *
   * @param id the Keycloak user id
   * @return the matching user
   */
  @GetMapping("/{id}")
  public ResponseEntity<ManagedUser> getUser(@PathVariable String id) {
    return ResponseEntity.ok(queryUsersUseCase.getUser(id));
  }

  /**
   * Creates a new, enabled user.
   *
   * @param request the new user's profile fields
   * @return the created user, 201
   */
  @PostMapping
  public ResponseEntity<ManagedUser> createUser(@Valid @RequestBody CreateUserRequest request) {
    ManagedUser created = manageUsersUseCase.createUser(request.toNewUser());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /**
   * Updates an existing user's profile fields.
   *
   * @param id the Keycloak user id
   * @param request the fields to update
   * @return the updated user, 200
   */
  @PutMapping("/{id}")
  public ResponseEntity<ManagedUser> updateUser(@PathVariable String id,
      @Valid @RequestBody UpdateUserRequest request) {
    ManagedUser updated = manageUsersUseCase.updateUser(id, request.toUserUpdate());
    return ResponseEntity.ok(updated);
  }

  /**
   * Enables or disables a user. Idempotent: applying the same state twice
   * succeeds both times with 200 (D4).
   *
   * @param id the Keycloak user id
   * @param request the desired enabled state
   * @return the updated user, 200
   */
  @PatchMapping("/{id}/status")
  public ResponseEntity<ManagedUser> setStatus(@PathVariable String id,
      @Valid @RequestBody SetUserStatusRequest request) {
    ManagedUser updated = manageUsersUseCase.setUserEnabled(id, request.enabled());
    return ResponseEntity.ok(updated);
  }

  /**
   * Resets a user's password.
   *
   * @param id the Keycloak user id
   * @param request the new password and whether it is temporary
   * @return 200 with no body
   */
  @PutMapping("/{id}/password")
  public ResponseEntity<Void> resetPassword(@PathVariable String id,
      @Valid @RequestBody ResetPasswordRequest request) {
    manageUsersUseCase.resetPassword(id, request.newPassword(), request.temporary());
    return ResponseEntity.ok().build();
  }
}
