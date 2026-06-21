package com.aegisnotify.notification.domain.model;

import com.aegisnotify.notification.domain.enums.OutboxStatus;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public final class OutboxEvent {

  private final UUID id;
  private final UUID notificationId;
  private final Map<String, Object> payload;
  private final OutboxStatus status;
  private final int retryCount;
  private final Instant createdAt;
  private final Instant processedAt;

  private OutboxEvent(UUID id, UUID notificationId, Map<String, Object> payload,
      OutboxStatus status, int retryCount, Instant createdAt,
      Instant processedAt) {
    this.id = id;
    this.notificationId = notificationId;
    this.payload = payload;
    this.status = status;
    this.retryCount = retryCount;
    this.createdAt = createdAt;
    this.processedAt = processedAt;
  }

  public static OutboxEvent create(UUID notificationId,
      Map<String, Object> payload) {
    return new OutboxEvent(
        UUID.randomUUID(), notificationId, Map.copyOf(payload),
        OutboxStatus.UNPROCESSED, 0, Instant.now(), null
    );
  }

  public static OutboxEvent reconstitute(UUID id, UUID notificationId,
      Map<String, Object> payload, OutboxStatus status, int retryCount,
      Instant createdAt, Instant processedAt) {
    return new OutboxEvent(
        id, notificationId, Map.copyOf(payload), status, retryCount,
        createdAt, processedAt
    );
  }

  public UUID getId() {
    return id;
  }

  public UUID getNotificationId() {
    return notificationId;
  }

  public Map<String, Object> getPayload() {
    return Collections.unmodifiableMap(payload);
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
