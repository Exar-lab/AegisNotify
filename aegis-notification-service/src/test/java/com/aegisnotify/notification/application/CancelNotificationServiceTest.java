package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.service.CancelNotificationService;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.exception.NotificationNotCancellableException;
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
class CancelNotificationServiceTest {

  @Mock
  private NotificationRepository notificationRepository;

  @Mock
  private NotificationLogRepository notificationLogRepository;

  @Mock
  private AuditEventPublisherPort auditEventPublisherPort;

  @InjectMocks
  private CancelNotificationService service;

  @Test
  void cancel_pendingNotification_returnsCancelledStatus() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NotificationResponse response = service.cancel(notificationId);

    assertEquals(notificationId, response.id());
    assertEquals(NotificationStatus.CANCELLED, response.status());
    verify(notificationRepository).save(any(Notification.class));

    ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
    verify(notificationLogRepository).save(logCaptor.capture());
    assertEquals(LogStatus.CANCELLED, logCaptor.getValue().getStatus());
    assertEquals(notificationId, logCaptor.getValue().getNotificationId());
  }

  @Test
  void cancel_queuedNotification_returnsCancelledStatus() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.SMS, "+34600000000", "otp",
        Map.of(), Priority.MEDIUM, NotificationStatus.QUEUED,
        null, null, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NotificationResponse response = service.cancel(notificationId);

    assertEquals(NotificationStatus.CANCELLED, response.status());
  }

  @Test
  void cancel_processingNotification_throwsNotCancellableException() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));

    assertThrows(NotificationNotCancellableException.class,
        () -> service.cancel(notificationId));
    verify(notificationRepository, never()).save(any(Notification.class));
  }

  @Test
  void cancel_sentNotification_throwsNotCancellableException() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.LOW, NotificationStatus.SENT,
        "SendGrid", null, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));

    assertThrows(NotificationNotCancellableException.class,
        () -> service.cancel(notificationId));
    verify(notificationRepository, never()).save(any(Notification.class));
  }

  @Test
  void cancel_notFound_throwsNotificationNotFoundException() {
    UUID notificationId = UUID.randomUUID();
    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.empty());

    assertThrows(NotificationNotFoundException.class,
        () -> service.cancel(notificationId));
  }

  @Test
  void cancel_pendingNotification_publishesCancelledAuditEvent() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.cancel(notificationId);

    ArgumentCaptor<AuditEventMessage> captor =
        ArgumentCaptor.forClass(AuditEventMessage.class);
    verify(auditEventPublisherPort).publish(captor.capture());

    AuditEventMessage captured = captor.getValue();
    assertEquals(notificationId, captured.notificationId());
    assertEquals("CANCELLED", captured.status());
    assertEquals("Notification cancelled", captured.details());
    assertEquals("EMAIL", captured.channel());
  }
}
