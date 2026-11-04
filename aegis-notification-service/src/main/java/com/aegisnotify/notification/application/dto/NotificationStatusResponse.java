package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.DeliveryAttempt;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Status and audit trail of a notification. {@code aggregation} was added
 * later (issue #225) as an additive field: existing consumers ignore it, and
 * it defaults to {@link AggregationInfo#none()}.
 */
public record NotificationStatusResponse(
    UUID id,
    Channel channel,
    String recipient,
    String templateName,
    NotificationStatus status,
    Instant createdAt,
    Instant updatedAt,
    List<NotificationLogEntry> auditTrail,
    Priority priority,
    UUID correlationId,
    List<DeliveryAttempt> providerAttempts,
    AggregationInfo aggregation
) {

  public NotificationStatusResponse {
    aggregation = aggregation == null ? AggregationInfo.none() : aggregation;
  }

  /** Pre-aggregation shape: no aggregation state. */
  public NotificationStatusResponse(UUID id, Channel channel, String recipient,
      String templateName, NotificationStatus status, Instant createdAt, Instant updatedAt,
      List<NotificationLogEntry> auditTrail, Priority priority, UUID correlationId,
      List<DeliveryAttempt> providerAttempts) {
    this(id, channel, recipient, templateName, status, createdAt, updatedAt, auditTrail,
        priority, correlationId, providerAttempts, AggregationInfo.none());
  }
}
