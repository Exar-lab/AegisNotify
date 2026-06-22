package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.port.out.MessageBrokerPort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.application.service.PublishOutboxEventService;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import java.time.Instant;
import java.util.HashMap;
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
class PublishOutboxEventServiceTest {

  @Mock
  private OutboxEventRepository outboxEventRepository;

  @Mock
  private MessageBrokerPort messageBrokerPort;

  @Mock
  private NotificationLogRepository notificationLogRepository;

  @Mock
  private NotificationRepository notificationRepository;

  @InjectMocks
  private PublishOutboxEventService service;

  @Test
  void publishPending_withPendingEvents_publishesAndMarksProcessed() {
    UUID notificationId = UUID.randomUUID();
    Map<String, Object> payload = new HashMap<>();
    payload.put("id", notificationId.toString());
    payload.put("priority", "HIGH");

    OutboxEvent event = OutboxEvent.create(notificationId, payload);
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );

    when(outboxEventRepository.findPendingEvents()).thenReturn(List.of(event));
    when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    int count = service.publishPending();

    assertEquals(1, count);
    verify(messageBrokerPort).publish(eq("high-priority-topic"), eq(event.getPayload()));
    verify(outboxEventRepository).save(any(OutboxEvent.class));
    verify(notificationLogRepository).save(any(NotificationLog.class));
    verify(notificationRepository).save(any(Notification.class));
  }

  @Test
  void publishPending_noPendingEvents_returnsZero() {
    when(outboxEventRepository.findPendingEvents()).thenReturn(List.of());

    int count = service.publishPending();

    assertEquals(0, count);
    verify(messageBrokerPort, never()).publish(any(), any());
    verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
  }

  @Test
  void publishPending_routesToCorrectTopicByPriority() {
    UUID highId = UUID.randomUUID();
    UUID mediumId = UUID.randomUUID();
    UUID lowId = UUID.randomUUID();

    OutboxEvent highEvent = OutboxEvent.create(highId,
        Map.of("priority", "HIGH", "id", highId.toString()));
    OutboxEvent mediumEvent = OutboxEvent.create(mediumId,
        Map.of("priority", "MEDIUM", "id", mediumId.toString()));
    OutboxEvent lowEvent = OutboxEvent.create(lowId,
        Map.of("priority", "LOW", "id", lowId.toString()));

    Notification highNotification = Notification.reconstitute(
        highId, Channel.EMAIL, "a@example.com", "t",
        Map.of(), Priority.HIGH, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );
    Notification mediumNotification = Notification.reconstitute(
        mediumId, Channel.EMAIL, "b@example.com", "t",
        Map.of(), Priority.MEDIUM, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );
    Notification lowNotification = Notification.reconstitute(
        lowId, Channel.EMAIL, "c@example.com", "t",
        Map.of(), Priority.LOW, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );

    when(outboxEventRepository.findPendingEvents())
        .thenReturn(List.of(highEvent, mediumEvent, lowEvent));
    when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationRepository.findById(highId))
        .thenReturn(Optional.of(highNotification));
    when(notificationRepository.findById(mediumId))
        .thenReturn(Optional.of(mediumNotification));
    when(notificationRepository.findById(lowId))
        .thenReturn(Optional.of(lowNotification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    int count = service.publishPending();

    assertEquals(3, count);
    verify(messageBrokerPort).publish(eq("high-priority-topic"), any());
    verify(messageBrokerPort).publish(eq("medium-priority-topic"), any());
    verify(messageBrokerPort).publish(eq("low-priority-topic"), any());
  }
}
