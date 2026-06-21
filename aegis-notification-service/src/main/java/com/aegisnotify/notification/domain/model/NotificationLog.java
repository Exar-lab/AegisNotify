package com.aegisnotify.notification.domain.model;

import com.aegisnotify.notification.domain.enums.LogStatus;
import java.time.Instant;
import java.util.UUID;

public final class NotificationLog {

  private final UUID id;
  private final UUID notificationId;
  private final LogStatus status;
  private final String details;
  private final Instant createdAt;

  private NotificationLog(UUID id, UUID notificationId, LogStatus status,
      String details, Instant createdAt) {
    this.id = id;
    this.notificationId = notificationId;
    this.status = status;
    this.details = details;
    this.createdAt = createdAt;
  }

  public static NotificationLog create(UUID notificationId, LogStatus status,
      String details) {
    return new NotificationLog(
        UUID.randomUUID(), notificationId, status, details, Instant.now()
    );
  }

  public static NotificationLog reconstitute(UUID id, UUID notificationId,
      LogStatus status, String details, Instant createdAt) {
    return new NotificationLog(id, notificationId, status, details, createdAt);
  }

  public UUID getId() {
    return id;
  }

  public UUID getNotificationId() {
    return notificationId;
  }

  public LogStatus getStatus() {
    return status;
  }

  public String getDetails() {
    return details;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
