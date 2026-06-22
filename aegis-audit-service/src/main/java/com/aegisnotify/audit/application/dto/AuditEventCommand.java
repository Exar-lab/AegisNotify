package com.aegisnotify.audit.application.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEventCommand(
    UUID notificationId,
    String status,
    String details,
    String channel,
    String recipient,
    String priority,
    Instant timestamp
) {
}
