package com.aegisnotify.notification.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PATCH /api/v1/templates/{id}/status}. Idempotent:
 * setting the same {@code active} value twice succeeds both times.
 *
 * <p>Boxed {@link Boolean} with {@code @NotNull} (mirroring
 * {@code SetUserStatusRequest} in aegis-user-service) so an omitted field is
 * rejected with 400 instead of silently deserializing to {@code false}.</p>
 *
 * @param active the desired active state
 */
public record SetTemplateStatusRequest(@NotNull Boolean active) {
}
