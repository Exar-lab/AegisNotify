package com.aegisnotify.notification.infrastructure.persistence.entity;

import com.aegisnotify.notification.domain.enums.LogStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_logs")
public class NotificationLogJpaEntity {

  @Id
  private UUID id;

  @Column(name = "notification_id", nullable = false)
  private UUID notificationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LogStatus status;

  private String details;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected NotificationLogJpaEntity() {
  }

  public NotificationLogJpaEntity(UUID id, UUID notificationId,
      LogStatus status, String details, Instant createdAt) {
    this.id = id;
    this.notificationId = notificationId;
    this.status = status;
    this.details = details;
    this.createdAt = createdAt;
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
