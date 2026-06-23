package com.aegisnotify.notification.application.port.out;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventPublisherPortTest {

  @Test
  void publish_withValidMessage_canBeMockedAndInvoked() {
    AuditEventPublisherPort port = mock(AuditEventPublisherPort.class);

    AuditEventMessage message = new AuditEventMessage(
        UUID.randomUUID(), "PENDING", "Notification created",
        "EMAIL", "user@example.com", "HIGH", Instant.now()
    );

    port.publish(message);

    verify(port).publish(message);
  }

  @Test
  void publish_withDifferentStatus_canBeMockedAndInvoked() {
    AuditEventPublisherPort port = mock(AuditEventPublisherPort.class);

    AuditEventMessage message = new AuditEventMessage(
        UUID.randomUUID(), "SENT_VIA_FALLBACK", "Fallback provider confirmation",
        "SMS", "+34600000000", "MEDIUM", Instant.now()
    );

    port.publish(message);

    verify(port).publish(message);
  }
}
