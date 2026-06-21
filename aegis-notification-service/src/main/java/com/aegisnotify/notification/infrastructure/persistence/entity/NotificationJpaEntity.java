package com.aegisnotify.notification.infrastructure.persistence.entity;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
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
@Table(name = "notifications")
public class NotificationJpaEntity {

  @Id
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Channel channel;

  @Column(nullable = false, length = 320)
  private String recipient;

  @Column(name = "template_name", nullable = false)
  private String templateName;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> parameters;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Priority priority;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NotificationStatus status;

  @Column(name = "provider_used")
  private String providerUsed;

  @Column(name = "error_detail")
  private String errorDetail;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected NotificationJpaEntity() {
  }

  public NotificationJpaEntity(UUID id, Channel channel, String recipient,
      String templateName, Map<String, Object> parameters, Priority priority,
      NotificationStatus status, String providerUsed, String errorDetail,
      Instant createdAt, Instant updatedAt) {
    this.id = id;
    this.channel = channel;
    this.recipient = recipient;
    this.templateName = templateName;
    this.parameters = parameters;
    this.priority = priority;
    this.status = status;
    this.providerUsed = providerUsed;
    this.errorDetail = errorDetail;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID getId() {
    return id;
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

  public Map<String, Object> getParameters() {
    return parameters;
  }

  public Priority getPriority() {
    return priority;
  }

  public NotificationStatus getStatus() {
    return status;
  }

  public String getProviderUsed() {
    return providerUsed;
  }

  public String getErrorDetail() {
    return errorDetail;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
