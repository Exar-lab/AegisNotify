package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.enums.NotificationStatus;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    NotificationStatus status
) {
}
