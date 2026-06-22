package com.aegisnotify.audit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.aegisnotify.audit.domain.enums.AuditStatus;
import com.aegisnotify.audit.domain.enums.Channel;
import com.aegisnotify.audit.domain.enums.Priority;
import com.aegisnotify.audit.domain.model.AuditEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventTest {

  @Test
  void create_setsAllFieldsCorrectly() {
    UUID notificationId = UUID.randomUUID();

    AuditEvent event = AuditEvent.create(
        notificationId, AuditStatus.SENT, "Delivered via SendGrid",
        Channel.EMAIL, "encrypted-recipient", Priority.HIGH
    );

    assertNotNull(event.getId());
    assertEquals(notificationId, event.getNotificationId());
    assertEquals(AuditStatus.SENT, event.getStatus());
    assertEquals("Delivered via SendGrid", event.getDetails());
    assertEquals(Channel.EMAIL, event.getChannel());
    assertEquals("encrypted-recipient", event.getRecipient());
    assertEquals(Priority.HIGH, event.getPriority());
    assertNotNull(event.getCreatedAt());
  }

  @Test
  void create_withDifferentValues_setsFieldsCorrectly() {
    UUID notificationId = UUID.randomUUID();

    AuditEvent event = AuditEvent.create(
        notificationId, AuditStatus.FAILED_CRITICAL, "All providers exhausted",
        Channel.SMS, "encrypted-phone", Priority.LOW
    );

    assertEquals(notificationId, event.getNotificationId());
    assertEquals(AuditStatus.FAILED_CRITICAL, event.getStatus());
    assertEquals("All providers exhausted", event.getDetails());
    assertEquals(Channel.SMS, event.getChannel());
    assertEquals("encrypted-phone", event.getRecipient());
    assertEquals(Priority.LOW, event.getPriority());
  }

  @Test
  void reconstitute_preservesAllFields() {
    UUID id = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-06-01T10:00:00Z");

    AuditEvent event = AuditEvent.reconstitute(
        id, notificationId, AuditStatus.QUEUED, "Queued for delivery",
        Channel.WHATSAPP, "+5491112345678", Priority.MEDIUM, createdAt
    );

    assertEquals(id, event.getId());
    assertEquals(notificationId, event.getNotificationId());
    assertEquals(AuditStatus.QUEUED, event.getStatus());
    assertEquals("Queued for delivery", event.getDetails());
    assertEquals(Channel.WHATSAPP, event.getChannel());
    assertEquals("+5491112345678", event.getRecipient());
    assertEquals(Priority.MEDIUM, event.getPriority());
    assertEquals(createdAt, event.getCreatedAt());
  }
}
