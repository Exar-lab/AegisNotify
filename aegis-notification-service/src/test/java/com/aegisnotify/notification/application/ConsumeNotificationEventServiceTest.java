package com.aegisnotify.notification.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.port.in.ProcessNotificationUseCase;
import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.service.ConsumeNotificationEventService;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.model.NotificationLog;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsumeNotificationEventServiceTest {

  @Mock
  private ProcessNotificationUseCase processNotificationUseCase;

  @Mock
  private DeadLetterQueuePort deadLetterQueuePort;

  @Mock
  private NotificationLogRepository notificationLogRepository;

  @InjectMocks
  private ConsumeNotificationEventService service;

  @Test
  void consume_successfulProcessing_doesNotSendToDlq() {
    UUID notificationId = UUID.randomUUID();
    NotificationResponse response = new NotificationResponse(
        notificationId, NotificationStatus.SENT
    );

    when(processNotificationUseCase.process(notificationId)).thenReturn(response);

    service.consume(notificationId);

    verify(processNotificationUseCase).process(notificationId);
    verify(deadLetterQueuePort, never()).sendToDlq(any(), any(), any());
  }

  @Test
  void consume_failedCritical_sendsToDlq() {
    UUID notificationId = UUID.randomUUID();
    NotificationResponse response = new NotificationResponse(
        notificationId, NotificationStatus.FAILED_CRITICAL
    );

    when(processNotificationUseCase.process(notificationId)).thenReturn(response);

    service.consume(notificationId);

    verify(deadLetterQueuePort).sendToDlq(eq(notificationId), any(Map.class),
        eq("Critical failure after processing"));
  }

  @Test
  void consume_unexpectedException_logAndSendToDlq() {
    UUID notificationId = UUID.randomUUID();

    when(processNotificationUseCase.process(notificationId))
        .thenThrow(new RuntimeException("Connection lost"));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.consume(notificationId);

    verify(notificationLogRepository).save(any(NotificationLog.class));
    verify(deadLetterQueuePort).sendToDlq(eq(notificationId), any(Map.class),
        eq("Unexpected error: Connection lost"));
  }
}
