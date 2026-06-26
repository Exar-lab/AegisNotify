package com.aegisnotify.notification.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit event message published to the audit topic on every notification lifecycle transition.
 *
 * @param notificationId the notification identifier
 * @param status the audit status string (mapped from NotificationStatus + context)
 * @param details human-readable transition context
 * @param channel the notification channel (EMAIL, SMS, WHATSAPP, PUSH)
 * @param recipient the notification recipient (plaintext; encrypted by consumer)
 * @param priority the notification priority (HIGH, MEDIUM, LOW)
 * @param timestamp the instant when the transition occurred
 */
public record AuditEventMessage(
    UUID notificationId,
    String status,
    String details,
    String channel,
    String recipient,
    String priority,
    Instant timestamp
) {
}
