package com.aegisnotify.audit.domain.model;

import com.aegisnotify.audit.domain.enums.AuditStatus;
import com.aegisnotify.audit.domain.enums.Channel;
import com.aegisnotify.audit.domain.enums.Priority;
import java.time.Instant;
import java.util.UUID;

public final class AuditEvent {

  private final UUID id;
  private final UUID notificationId;
  private final AuditStatus status;
  private final String details;
  private final Channel channel;
  private final String recipient;
  private final Priority priority;
  private final Instant createdAt;

  private AuditEvent(UUID id, UUID notificationId, AuditStatus status,
      String details, Channel channel, String recipient, Priority priority,
      Instant createdAt) {
    this.id = id;
    this.notificationId = notificationId;
    this.status = status;
    this.details = details;
    this.channel = channel;
    this.recipient = recipient;
    this.priority = priority;
    this.createdAt = createdAt;
  }

  public static AuditEvent create(UUID notificationId, AuditStatus status,
      String details, Channel channel, String recipient, Priority priority) {
    return new AuditEvent(
        UUID.randomUUID(), notificationId, status, details,
        channel, recipient, priority, Instant.now()
    );
  }

  public static AuditEvent reconstitute(UUID id, UUID notificationId,
      AuditStatus status, String details, Channel channel, String recipient,
      Priority priority, Instant createdAt) {
    return new AuditEvent(
        id, notificationId, status, details, channel, recipient,
        priority, createdAt
    );
  }

  public UUID getId() {
    return id;
  }

  public UUID getNotificationId() {
    return notificationId;
  }

  public AuditStatus getStatus() {
    return status;
  }

  public String getDetails() {
    return details;
  }

  public Channel getChannel() {
    return channel;
  }

  public String getRecipient() {
    return recipient;
  }

  public Priority getPriority() {
    return priority;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
