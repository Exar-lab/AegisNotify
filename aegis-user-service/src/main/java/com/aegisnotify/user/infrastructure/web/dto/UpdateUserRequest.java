package com.aegisnotify.user.infrastructure.web.dto;

import com.aegisnotify.user.application.dto.UserUpdate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/v1/users/{id}}. Username is
 * intentionally not settable here — see {@code UserUpdate}'s javadoc.
 */
public record UpdateUserRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 255) String firstName,
    @NotBlank @Size(max = 255) String lastName) {

  public UserUpdate toUserUpdate() {
    return new UserUpdate(email, firstName, lastName);
  }
}
