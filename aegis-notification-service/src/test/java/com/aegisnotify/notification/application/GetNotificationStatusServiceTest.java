package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.NotificationStatusResponse;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.service.GetNotificationStatusService;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.exception.NotificationNotFoundException;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetNotificationStatusServiceTest {

  @Mock
  private NotificationRepository notificationRepository;

  @Mock
  private NotificationLogRepository notificationLogRepository;

  @InjectMocks
  private GetNotificationStatusService service;

  @Test
  void getStatus_found_returnsStatusWithAuditTrail() {
    UUID notificationId = UUID.randomUUID();
    Instant now = Instant.now();

    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH, NotificationStatus.PENDING,
        null, null, now, now
    );
    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));

    NotificationLog log1 = NotificationLog.reconstitute(
        UUID.randomUUID(), notificationId, LogStatus.PENDING,
        "Notification accepted", now
    );
    NotificationLog log2 = NotificationLog.reconstitute(
        UUID.randomUUID(), notificationId, LogStatus.QUEUED,
        "Queued for delivery", now.plusSeconds(1)
    );
    when(notificationLogRepository.findByNotificationIdOrderByCreatedAt(notificationId))
        .thenReturn(List.of(log1, log2));

    NotificationStatusResponse response = service.getStatus(notificationId);

    assertEquals(notificationId, response.id());
    assertEquals(Channel.EMAIL, response.channel());
    assertEquals("user@example.com", response.recipient());
    assertEquals("welcome", response.templateName());
    assertEquals(NotificationStatus.PENDING, response.status());
    assertEquals(2, response.auditTrail().size());
    assertEquals(LogStatus.PENDING, response.auditTrail().get(0).status());
    assertEquals(LogStatus.QUEUED, response.auditTrail().get(1).status());
  }

  @Test
  void getStatus_notFound_throwsNotificationNotFoundException() {
    UUID notificationId = UUID.randomUUID();
    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.empty());

    assertThrows(NotificationNotFoundException.class,
        () -> service.getStatus(notificationId));
  }
}
