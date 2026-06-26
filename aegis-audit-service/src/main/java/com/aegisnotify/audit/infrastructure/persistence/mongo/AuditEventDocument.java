package com.aegisnotify.audit.infrastructure.persistence.mongo;

import java.time.Instant;
import java.util.UUID;

/**
 * Embedded document representing a single audit event within an
 * {@link AuditTrailDocument}.
 *
 * @param id unique event identifier
 * @param status audit status name
 * @param details human-readable transition context
 * @param channel notification channel
 * @param recipient encrypted recipient value
 * @param priority notification priority
 * @param createdAt timestamp of the event
 */
public record AuditEventDocument(
    UUID id,
    String status,
    String details,
    String channel,
    String recipient,
    String priority,
    Instant createdAt
) {
}
