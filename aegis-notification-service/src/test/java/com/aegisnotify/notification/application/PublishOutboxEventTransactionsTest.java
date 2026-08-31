package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import com.aegisnotify.notification.application.port.out.MessageBrokerPort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.application.service.PublishOutboxEventTransactions;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers {@link PublishOutboxEventTransactions#publishOne(OutboxEvent)} — the
 * per-event transactional unit that {@link PublishOutboxEventServiceTest}'s
 * batch loop delegates to. Each call is its own {@code @Transactional}
 * boundary, so a broker failure here must never persist a
 * {@code PROCESSED} outbox row (K3), independent of any other event in the
 * batch.
 */
@ExtendWith(MockitoExtension.class)
class PublishOutboxEventTransactionsTest {

  @Mock
  private OutboxEventRepository outboxEventRepository;

  @Mock
  private MessageBrokerPort messageBrokerPort;

  @Mock
  private NotificationLogRepository notificationLogRepository;

  @Mock
  private NotificationRepository notificationRepository;

  @Mock
  private AuditEventPublisherPort auditEventPublisherPort;

  private PublishOutboxEventTransactions transactions;

  @BeforeEach
  void setUp() {
    transactions = new PublishOutboxEventTransactions(outboxEventRepository, messageBrokerPort,
        notificationLogRepository, notificationRepository, auditEventPublisherPort);
  }

  @Test
  void publishOne_highPriorityEvent_publishesToHighPriorityTopicAndMarksProcessed() {
    UUID notificationId = UUID.randomUUID();
    Map<String, Object> payload = new HashMap<>();
    payload.put("id", notificationId.toString());
    payload.put("priority", "HIGH");

    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );

    when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    OutboxEvent event = OutboxEvent.create(notificationId, payload);
    transactions.publishOne(event);

    verify(messageBrokerPort).publish(eq("high-priority-topic"), eq(event.getPayload()));
    verify(outboxEventRepository).save(any(OutboxEvent.class));
    verify(notificationLogRepository).save(any(NotificationLog.class));
    verify(notificationRepository).save(any(Notification.class));
  }

  @Test
  void publishOne_mediumPriorityEvent_publishesToMediumPriorityTopic() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "b@example.com", "t",
        Map.of(), Priority.MEDIUM, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );

    when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    OutboxEvent event = OutboxEvent.create(notificationId,
        Map.of("id", notificationId.toString(), "priority", "MEDIUM"));
    transactions.publishOne(event);

    verify(messageBrokerPort).publish(eq("medium-priority-topic"), any());
  }

  @Test
  void publishOne_lowPriorityEvent_publishesToLowPriorityTopic() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "c@example.com", "t",
        Map.of(), Priority.LOW, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );

    when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    OutboxEvent event = OutboxEvent.create(notificationId,
        Map.of("id", notificationId.toString(), "priority", "LOW"));
    transactions.publishOne(event);

    verify(messageBrokerPort).publish(eq("low-priority-topic"), any());
  }

  @Test
  void publishOne_publishesAuditEventWithQueuedStatus() {
    UUID notificationId = UUID.randomUUID();
    Map<String, Object> payload = new HashMap<>();
    payload.put("id", notificationId.toString());
    payload.put("priority", "HIGH");
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );

    when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    OutboxEvent event = OutboxEvent.create(notificationId, payload);
    transactions.publishOne(event);

    ArgumentCaptor<AuditEventMessage> captor = ArgumentCaptor.forClass(AuditEventMessage.class);
    verify(auditEventPublisherPort).publish(captor.capture());

    AuditEventMessage captured = captor.getValue();
    assertEquals(notificationId, captured.notificationId());
    assertEquals("QUEUED", captured.status());
    assertEquals("EMAIL", captured.channel());
    assertEquals("user@example.com", captured.recipient());
    assertEquals("HIGH", captured.priority());
  }

  @Test
  void publishOne_whenMessageBrokerFails_neverMarksOutboxEventProcessed() {
    UUID notificationId = UUID.randomUUID();
    OutboxEvent event = OutboxEvent.create(notificationId,
        Map.of("id", notificationId.toString(), "priority", "HIGH"));

    doThrow(new RuntimeException("broker unavailable"))
        .when(messageBrokerPort).publish(eq("high-priority-topic"), any());

    assertThrows(RuntimeException.class, () -> transactions.publishOne(event));

    verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    verify(notificationLogRepository, never()).save(any(NotificationLog.class));
    verify(notificationRepository, never()).save(any(Notification.class));
    verify(auditEventPublisherPort, never()).publish(any());
  }
}
