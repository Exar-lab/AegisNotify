package com.aegisnotify.notification.infrastructure.persistence.entity;

import com.aegisnotify.notification.domain.enums.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEventJpaEntity {

  @Id
  private UUID id;

  @Column(name = "notification_id", nullable = false)
  private UUID notificationId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OutboxStatus status;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  protected OutboxEventJpaEntity() {
  }

  public OutboxEventJpaEntity(UUID id, UUID notificationId,
      Map<String, Object> payload, OutboxStatus status, int retryCount,
      Instant createdAt, Instant processedAt) {
    this.id = id;
    this.notificationId = notificationId;
    this.payload = payload;
    this.status = status;
    this.retryCount = retryCount;
    this.createdAt = createdAt;
    this.processedAt = processedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getNotificationId() {
    return notificationId;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  public OutboxStatus getStatus() {
    return status;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
