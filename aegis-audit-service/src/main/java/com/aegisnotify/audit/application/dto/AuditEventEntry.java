package com.aegisnotify.audit.application.dto;

import java.time.Instant;

public record AuditEventEntry(
    String status,
    String details,
    Instant createdAt
) {
}
