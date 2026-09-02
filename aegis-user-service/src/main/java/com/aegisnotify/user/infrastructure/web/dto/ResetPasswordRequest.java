package com.aegisnotify.user.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/v1/users/{id}/password}.
 *
 * <p>{@code temporary} defaults to {@code true} when omitted: a
 * password reset is assumed to be a one-time credential that Keycloak
 * forces the user to change on next login, matching the security-conscious
 * default for admin-initiated password resets. Callers may explicitly pass
 * {@code false} to set a permanent password instead.</p>
 *
 * @param newPassword the new password value
 * @param temporary whether Keycloak should force a change on next login;
 *     defaults to {@code true} when not provided
 */
public record ResetPasswordRequest(
    @NotBlank @Size(min = 8, max = 255) String newPassword,
    Boolean temporary) {

  public ResetPasswordRequest {
    if (temporary == null) {
      temporary = true;
    }
  }
}
