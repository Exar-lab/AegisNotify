package com.aegisnotify.notification.application.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventMessageTest {

  @Test
  void constructor_allFields_createsRecordWithCorrectValues() {
    UUID notificationId = UUID.randomUUID();
    Instant timestamp = Instant.now();

    AuditEventMessage message = new AuditEventMessage(
        notificationId, "PENDING", "Notification created",
        "EMAIL", "user@example.com", "HIGH", timestamp
    );

    assertEquals(notificationId, message.notificationId());
    assertEquals("PENDING", message.status());
    assertEquals("Notification created", message.details());
    assertEquals("EMAIL", message.channel());
    assertEquals("user@example.com", message.recipient());
    assertEquals("HIGH", message.priority());
    assertEquals(timestamp, message.timestamp());
  }

  @Test
  void constructor_nullDetails_allowsNullForOptionalFields() {
    UUID notificationId = UUID.randomUUID();
    Instant timestamp = Instant.now();

    AuditEventMessage message = new AuditEventMessage(
        notificationId, "SENT", null,
        "SMS", "+34600000000", "MEDIUM", timestamp
    );

    assertNotNull(message.notificationId());
    assertEquals("SENT", message.status());
    assertNull(message.details());
    assertEquals("SMS", message.channel());
    assertEquals("+34600000000", message.recipient());
    assertEquals("MEDIUM", message.priority());
  }

  @Test
  void equality_sameValues_recordsAreEqual() {
    UUID notificationId = UUID.randomUUID();
    Instant timestamp = Instant.parse("2026-01-15T10:30:00Z");

    AuditEventMessage first = new AuditEventMessage(
        notificationId, "QUEUED", "Published to outbox",
        "WHATSAPP", "+34600000001", "LOW", timestamp
    );
    AuditEventMessage second = new AuditEventMessage(
        notificationId, "QUEUED", "Published to outbox",
        "WHATSAPP", "+34600000001", "LOW", timestamp
    );

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }
}
