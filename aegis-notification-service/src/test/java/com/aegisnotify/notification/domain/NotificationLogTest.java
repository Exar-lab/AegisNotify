package com.aegisnotify.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.model.NotificationLog;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationLogTest {

  @Test
  void create_setsFieldsCorrectly() {
    UUID notificationId = UUID.randomUUID();
    NotificationLog log = NotificationLog.create(
        notificationId, LogStatus.PENDING, "Notification accepted"
    );

    assertNotNull(log.getId());
    assertEquals(notificationId, log.getNotificationId());
    assertEquals(LogStatus.PENDING, log.getStatus());
    assertEquals("Notification accepted", log.getDetails());
    assertNotNull(log.getCreatedAt());
  }
}
