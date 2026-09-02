package com.aegisnotify.user.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PATCH /api/v1/users/{id}/status}. This is the
 * ONLY lifecycle mutation exposed by the API — there is no delete (D4).
 * Idempotent: setting the same {@code enabled} value twice succeeds both
 * times.
 *
 * @param enabled the desired enabled state
 */
public record SetUserStatusRequest(@NotNull Boolean enabled) {
}
