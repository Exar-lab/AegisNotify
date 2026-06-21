package com.aegisnotify.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.aegisnotify.notification.domain.enums.OutboxStatus;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

  @Test
  void create_setsUnprocessedStatus() {
    UUID notificationId = UUID.randomUUID();
    OutboxEvent event = OutboxEvent.create(
        notificationId, Map.of("key", "value")
    );

    assertNotNull(event.getId());
    assertEquals(OutboxStatus.UNPROCESSED, event.getStatus());
    assertEquals(0, event.getRetryCount());
    assertEquals(notificationId, event.getNotificationId());
  }

  @Test
  void create_payloadDefensivelyCopied() {
    Map<String, Object> originalPayload = new HashMap<>();
    originalPayload.put("key", "value");

    OutboxEvent event = OutboxEvent.create(UUID.randomUUID(), originalPayload);

    originalPayload.put("extra", "should-not-appear");

    assertEquals(1, event.getPayload().size());
    assertEquals("value", event.getPayload().get("key"));
  }
}
