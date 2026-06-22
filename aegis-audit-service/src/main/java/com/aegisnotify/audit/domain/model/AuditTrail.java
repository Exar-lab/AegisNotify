package com.aegisnotify.audit.domain.model;

import com.aegisnotify.audit.domain.enums.AuditStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class AuditTrail {

  private final UUID notificationId;
  private final AuditStatus currentStatus;
  private final List<AuditEvent> events;
  private final Instant createdAt;
  private final Instant updatedAt;

  private AuditTrail(UUID notificationId, AuditStatus currentStatus,
      List<AuditEvent> events, Instant createdAt, Instant updatedAt) {
    this.notificationId = notificationId;
    this.currentStatus = currentStatus;
    this.events = List.copyOf(events);
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static AuditTrail create(UUID notificationId, AuditEvent firstEvent) {
    Instant now = Instant.now();
    return new AuditTrail(
        notificationId, firstEvent.getStatus(),
        List.of(firstEvent), now, now
    );
  }

  public static AuditTrail reconstitute(UUID notificationId,
      AuditStatus currentStatus, List<AuditEvent> events,
      Instant createdAt, Instant updatedAt) {
    return new AuditTrail(
        notificationId, currentStatus, events, createdAt, updatedAt
    );
  }

  public AuditTrail appendEvent(AuditEvent event) {
    List<AuditEvent> updatedEvents = new ArrayList<>(this.events);
    updatedEvents.add(event);
    return new AuditTrail(
        this.notificationId, event.getStatus(),
        updatedEvents, this.createdAt, Instant.now()
    );
  }

  public UUID getNotificationId() {
    return notificationId;
  }

  public AuditStatus getCurrentStatus() {
    return currentStatus;
  }

  public List<AuditEvent> getEvents() {
    return Collections.unmodifiableList(events);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
