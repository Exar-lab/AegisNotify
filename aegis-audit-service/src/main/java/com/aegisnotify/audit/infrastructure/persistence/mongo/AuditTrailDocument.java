package com.aegisnotify.audit.infrastructure.persistence.mongo;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document representing an audit trail for a notification.
 *
 * <p>Uses the {@code notificationId} as the document {@code _id}. Events
 * are stored as an embedded array and appended via {@code $push}.</p>
 *
 * @param id the notificationId string used as MongoDB _id
 * @param currentStatus the latest audit status
 * @param events embedded ordered list of audit events
 * @param createdAt trail creation timestamp
 * @param updatedAt last update timestamp
 */
@Document(collection = "audit_trails")
public record AuditTrailDocument(
    @Id String id,
    String currentStatus,
    List<AuditEventDocument> events,
    Instant createdAt,
    Instant updatedAt
) {
}
