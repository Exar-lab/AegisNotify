package com.aegisnotify.audit.application.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEventSummary(
    UUID notificationId,
    String currentStatus,
    String channel,
    String priority,
    Instant createdAt
) {
}
