package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationStatusResponse(
    UUID id,
    Channel channel,
    String recipient,
    String templateName,
    NotificationStatus status,
    Instant createdAt,
    Instant updatedAt,
    List<NotificationLogEntry> auditTrail
) {
}
