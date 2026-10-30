package com.aegisnotify.user.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request payload for setting a user's realm roles.
 *
 * @param roles list of non-blank role names to assign
 */
public record SetUserRolesRequest(
    @NotNull(message = "roles list must not be null")
    List<@NotBlank(message = "role name must not be blank") String> roles
) {}
