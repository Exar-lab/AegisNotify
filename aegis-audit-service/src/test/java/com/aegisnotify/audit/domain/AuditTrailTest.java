package com.aegisnotify.audit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aegisnotify.audit.domain.enums.AuditStatus;
import com.aegisnotify.audit.domain.enums.Channel;
import com.aegisnotify.audit.domain.enums.Priority;
import com.aegisnotify.audit.domain.model.AuditEvent;
import com.aegisnotify.audit.domain.model.AuditTrail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditTrailTest {

  @Test
  void create_withFirstEvent_setsFieldsCorrectly() {
    UUID notificationId = UUID.randomUUID();
    AuditEvent firstEvent = AuditEvent.create(
        notificationId, AuditStatus.PENDING, "Notification accepted",
        Channel.EMAIL, "encrypted-recipient", Priority.HIGH
    );

    AuditTrail trail = AuditTrail.create(notificationId, firstEvent);

    assertEquals(notificationId, trail.getNotificationId());
    assertEquals(AuditStatus.PENDING, trail.getCurrentStatus());
    assertEquals(1, trail.getEvents().size());
    assertEquals(firstEvent, trail.getEvents().get(0));
    assertNotNull(trail.getCreatedAt());
    assertNotNull(trail.getUpdatedAt());
  }

  @Test
  void appendEvent_returnsNewInstanceWithUpdatedStatus() {
    UUID notificationId = UUID.randomUUID();
    AuditEvent firstEvent = AuditEvent.create(
        notificationId, AuditStatus.PENDING, "Accepted",
        Channel.SMS, "encrypted-phone", Priority.MEDIUM
    );
    AuditTrail original = AuditTrail.create(notificationId, firstEvent);

    AuditEvent secondEvent = AuditEvent.create(
        notificationId, AuditStatus.QUEUED, "Queued for delivery",
        Channel.SMS, "encrypted-phone", Priority.MEDIUM
    );
    AuditTrail updated = original.appendEvent(secondEvent);

    assertNotSame(original, updated);
    assertEquals(1, original.getEvents().size());
    assertEquals(2, updated.getEvents().size());
    assertEquals(AuditStatus.QUEUED, updated.getCurrentStatus());
    assertEquals(AuditStatus.PENDING, original.getCurrentStatus());
  }

  @Test
  void appendEvent_multipleEvents_preservesOrder() {
    UUID notificationId = UUID.randomUUID();
    AuditEvent event1 = AuditEvent.create(
        notificationId, AuditStatus.PENDING, "Accepted",
        Channel.EMAIL, "encrypted", Priority.HIGH
    );
    AuditTrail trail = AuditTrail.create(notificationId, event1);

    AuditEvent event2 = AuditEvent.create(
        notificationId, AuditStatus.QUEUED, "Queued",
        Channel.EMAIL, "encrypted", Priority.HIGH
    );
    trail = trail.appendEvent(event2);

    AuditEvent event3 = AuditEvent.create(
        notificationId, AuditStatus.SENT, "Sent",
        Channel.EMAIL, "encrypted", Priority.HIGH
    );
    trail = trail.appendEvent(event3);

    assertEquals(3, trail.getEvents().size());
    assertEquals(AuditStatus.PENDING, trail.getEvents().get(0).getStatus());
    assertEquals(AuditStatus.QUEUED, trail.getEvents().get(1).getStatus());
    assertEquals(AuditStatus.SENT, trail.getEvents().get(2).getStatus());
    assertEquals(AuditStatus.SENT, trail.getCurrentStatus());
  }

  @Test
  void getEvents_returnsDefensiveCopy() {
    UUID notificationId = UUID.randomUUID();
    AuditEvent event = AuditEvent.create(
        notificationId, AuditStatus.PENDING, "Accepted",
        Channel.PUSH, "token", Priority.LOW
    );
    AuditTrail trail = AuditTrail.create(notificationId, event);

    List<AuditEvent> events = trail.getEvents();

    assertThrows(UnsupportedOperationException.class, () ->
        events.add(AuditEvent.create(
            notificationId, AuditStatus.SENT, "Sent",
            Channel.PUSH, "token", Priority.LOW
        ))
    );
  }

  @Test
  void reconstitute_preservesAllFields() {
    UUID notificationId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-06-01T10:00:00Z");
    Instant updatedAt = Instant.parse("2026-06-01T10:05:00Z");
    AuditEvent event = AuditEvent.reconstitute(
        UUID.randomUUID(), notificationId, AuditStatus.SENT, "Sent",
        Channel.EMAIL, "encrypted", Priority.HIGH, createdAt
    );

    AuditTrail trail = AuditTrail.reconstitute(
        notificationId, AuditStatus.SENT, List.of(event), createdAt, updatedAt
    );

    assertEquals(notificationId, trail.getNotificationId());
    assertEquals(AuditStatus.SENT, trail.getCurrentStatus());
    assertEquals(1, trail.getEvents().size());
    assertEquals(createdAt, trail.getCreatedAt());
    assertEquals(updatedAt, trail.getUpdatedAt());
  }
}
