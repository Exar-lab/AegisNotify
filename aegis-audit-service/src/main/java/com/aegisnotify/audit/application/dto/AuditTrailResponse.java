package com.aegisnotify.audit.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditTrailResponse(
    UUID notificationId,
    String currentStatus,
    List<AuditEventEntry> events,
    Instant createdAt,
    Instant updatedAt
) {
}
