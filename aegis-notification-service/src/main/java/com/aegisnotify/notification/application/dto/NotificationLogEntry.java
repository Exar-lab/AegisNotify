package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.enums.LogStatus;
import java.time.Instant;

public record NotificationLogEntry(
    LogStatus status,
    String details,
    Instant timestamp
) {
}
