package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.service.SendToDlqService;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.exception.NotificationNotFoundException;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendToDlqServiceTest {

  @Mock
  private NotificationRepository notificationRepository;

  @Mock
  private NotificationLogRepository notificationLogRepository;

  @Mock
  private DeadLetterQueuePort deadLetterQueuePort;

  @Mock
  private AuditEventPublisherPort auditEventPublisherPort;

  @InjectMocks
  private SendToDlqService service;

  @Test
  void sendToDlq_existingNotification_marksFailedCriticalAndSendsToDlq() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NotificationResponse response = service.sendToDlq(notificationId, "Manual intervention");

    assertEquals(notificationId, response.id());
    assertEquals(NotificationStatus.FAILED_CRITICAL, response.status());
    verify(notificationRepository).save(any(Notification.class));
    verify(notificationLogRepository).save(any(NotificationLog.class));
    verify(deadLetterQueuePort).sendToDlq(eq(notificationId), any(Map.class),
        eq("Manual intervention"));
  }

  @Test
  void sendToDlq_notFound_throwsNotificationNotFoundException() {
    UUID notificationId = UUID.randomUUID();
    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.empty());

    assertThrows(NotificationNotFoundException.class,
        () -> service.sendToDlq(notificationId, "Some reason"));

    verify(notificationRepository, never()).save(any(Notification.class));
    verify(deadLetterQueuePort, never()).sendToDlq(any(), any(), any());
  }

  @Test
  void sendToDlq_existingNotification_publishesFailedCriticalAuditEvent() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.sendToDlq(notificationId, "Manual intervention");

    ArgumentCaptor<AuditEventMessage> captor =
        ArgumentCaptor.forClass(AuditEventMessage.class);
    verify(auditEventPublisherPort).publish(captor.capture());

    AuditEventMessage captured = captor.getValue();
    assertEquals(notificationId, captured.notificationId());
    assertEquals("FAILED_CRITICAL", captured.status());
    assertEquals("EMAIL", captured.channel());
    assertEquals("user@example.com", captured.recipient());
  }
}
