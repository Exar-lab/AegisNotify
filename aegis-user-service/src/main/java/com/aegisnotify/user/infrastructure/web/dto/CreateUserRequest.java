package com.aegisnotify.user.infrastructure.web.dto;

import com.aegisnotify.user.application.dto.NewUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/users}. New users are always
 * created enabled; there is no {@code enabled} field here — disabling
 * happens exclusively through {@code PATCH /api/v1/users/{id}/status}.
 */
public record CreateUserRequest(
    @NotBlank @Size(max = 255) String username,
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 255) String firstName,
    @NotBlank @Size(max = 255) String lastName) {

  public NewUser toNewUser() {
    return new NewUser(username, email, firstName, lastName);
  }
}
