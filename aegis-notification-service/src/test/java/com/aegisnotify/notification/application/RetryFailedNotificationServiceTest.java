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
import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.application.service.RetryFailedNotificationService;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.exception.NotificationNotFoundException;
import com.aegisnotify.notification.domain.exception.NotificationNotRetryableException;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.domain.model.OutboxEvent;
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
class RetryFailedNotificationServiceTest {

  @Mock
  private NotificationRepository notificationRepository;

  @Mock
  private NotificationLogRepository notificationLogRepository;

  @Mock
  private OutboxEventRepository outboxEventRepository;

  @Mock
  private AuditEventPublisherPort auditEventPublisherPort;

  @InjectMocks
  private RetryFailedNotificationService service;

  @Test
  void retry_failedNotification_resetsToPendingAndCreatesOutboxEvent() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH, NotificationStatus.FAILED,
        "SendGrid", "Timeout", Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NotificationResponse response = service.retry(notificationId);

    assertEquals(notificationId, response.id());
    assertEquals(NotificationStatus.PENDING, response.status());
    verify(notificationRepository).save(any(Notification.class));
    verify(outboxEventRepository).save(any(OutboxEvent.class));
    verify(notificationLogRepository).save(any(NotificationLog.class));
  }

  @Test
  void retry_sentNotification_throwsNotRetryableException() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH, NotificationStatus.SENT,
        "SendGrid", null, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));

    assertThrows(NotificationNotRetryableException.class,
        () -> service.retry(notificationId));
    verify(notificationRepository, never()).save(any(Notification.class));
    verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
  }

  @Test
  void retry_failedCriticalNotification_throwsNotRetryableException() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.SMS, "+34600000000", "otp",
        Map.of(), Priority.HIGH, NotificationStatus.FAILED_CRITICAL,
        null, "All providers exhausted", Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));

    assertThrows(NotificationNotRetryableException.class,
        () -> service.retry(notificationId));
    verify(notificationRepository, never()).save(any(Notification.class));
  }

  @Test
  void retry_notFound_throwsNotificationNotFoundException() {
    UUID notificationId = UUID.randomUUID();
    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.empty());

    assertThrows(NotificationNotFoundException.class,
        () -> service.retry(notificationId));
  }

  @Test
  void retry_failedNotification_publishesPendingAuditEvent() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH, NotificationStatus.FAILED,
        "SendGrid", "Timeout", Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.retry(notificationId);

    ArgumentCaptor<AuditEventMessage> captor =
        ArgumentCaptor.forClass(AuditEventMessage.class);
    verify(auditEventPublisherPort).publish(captor.capture());

    AuditEventMessage captured = captor.getValue();
    assertEquals(notificationId, captured.notificationId());
    assertEquals("PENDING", captured.status());
    assertEquals("Retry initiated", captured.details());
    assertEquals("EMAIL", captured.channel());
  }
}
