package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import java.time.Instant;
import java.util.UUID;

public record NotificationSummary(
    UUID id,
    Channel channel,
    String recipient,
    String templateName,
    NotificationStatus status,
    Priority priority,
    Instant createdAt,
    String providerUsed
) {
  public NotificationSummary(UUID id, Channel channel, String recipient, String templateName,
      NotificationStatus status, Priority priority, Instant createdAt) {
    this(id, channel, recipient, templateName, status, priority, createdAt, null);
  }
}
