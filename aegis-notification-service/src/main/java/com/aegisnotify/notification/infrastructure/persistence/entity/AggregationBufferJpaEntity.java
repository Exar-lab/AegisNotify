package com.aegisnotify.notification.infrastructure.persistence.entity;

import com.aegisnotify.notification.domain.enums.AggregationBufferStatus;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aggregation_buffer")
public class AggregationBufferJpaEntity {

  @Id
  private UUID id;

  @Column(name = "notification_id", nullable = false)
  private UUID notificationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Channel channel;

  @Column(nullable = false, length = 320)
  private String recipient;

  @Column(name = "template_name")
  private String templateName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Priority priority;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AggregationBufferStatus status;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AggregationBufferJpaEntity() {
  }

  public AggregationBufferJpaEntity(UUID id, UUID notificationId, Channel channel,
      String recipient, String templateName, Priority priority,
      AggregationBufferStatus status, Instant expiresAt, Instant claimedAt,
      int attempts, Instant createdAt) {
    this.id = id;
    this.notificationId = notificationId;
    this.channel = channel;
    this.recipient = recipient;
    this.templateName = templateName;
    this.priority = priority;
    this.status = status;
    this.expiresAt = expiresAt;
    this.claimedAt = claimedAt;
    this.attempts = attempts;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getNotificationId() {
    return notificationId;
  }

  public Channel getChannel() {
    return channel;
  }

  public String getRecipient() {
    return recipient;
  }

  public String getTemplateName() {
    return templateName;
  }

  public Priority getPriority() {
    return priority;
  }

  public AggregationBufferStatus getStatus() {
    return status;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getClaimedAt() {
    return claimedAt;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
