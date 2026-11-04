package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.SummarizedContent;
import com.aegisnotify.notification.application.port.out.AggregationBufferRepository;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.application.service.AggregationFlushTransactions;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.AggregationSettings;
import com.aegisnotify.notification.domain.model.BufferedNotification;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Issue #227: how {@link AggregationFlushTransactions#flushAggregate} applies
 * the model-suggested priority — only when the flag is on, never below the
 * highest member priority, with the suggested and applied values recorded in
 * the leader's notification log.
 */
@ExtendWith(MockitoExtension.class)
class AggregationFlushTransactionsPriorityTest {

  @Mock
  private AggregationBufferRepository aggregationBufferRepository;

  @Mock
  private NotificationRepository notificationRepository;

  @Mock
  private OutboxEventRepository outboxEventRepository;

  @Mock
  private NotificationLogRepository notificationLogRepository;

  @Mock
  private AuditEventPublisherPort auditEventPublisherPort;

  private final Instant now = Instant.now();
  private final UUID leaderId = UUID.randomUUID();
  private final UUID siblingId = UUID.randomUUID();

  @BeforeEach
  void stubPersistence() {
    org.mockito.Mockito.lenient().when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    org.mockito.Mockito.lenient().when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    org.mockito.Mockito.lenient().when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private AggregationFlushTransactions transactions(boolean modelPriorityEnabled) {
    AggregationSettings settings = new AggregationSettings(true, Duration.ofMinutes(5), true, 20,
        Set.of(), Set.of(), Duration.ofMinutes(2), 3, modelPriorityEnabled);
    return new AggregationFlushTransactions(aggregationBufferRepository, notificationRepository,
        outboxEventRepository, notificationLogRepository, auditEventPublisherPort, settings);
  }

  private Notification notification(UUID id, Priority priority, long ageSeconds) {
    return Notification.reconstitute(id, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "Jane"), priority, NotificationStatus.PENDING, null, null,
        now.minusSeconds(ageSeconds), now);
  }

  private BufferedNotification row(UUID notificationId, Priority priority, long ageSeconds) {
    return BufferedNotification.create(notificationId, Channel.EMAIL, "user@example.com",
        "welcome", priority, now.plusSeconds(300), now.minusSeconds(ageSeconds)).claim(now);
  }

  /** Flushes a leader + one sibling and returns the leader's outbox event. */
  private OutboxEvent flush(boolean flag, Priority leaderPriority, Priority siblingPriority,
      Priority suggested) {
    when(notificationRepository.findById(leaderId))
        .thenReturn(Optional.of(notification(leaderId, leaderPriority, 10)));
    org.mockito.Mockito.lenient().when(notificationRepository.findById(siblingId))
        .thenReturn(Optional.of(notification(siblingId, siblingPriority, 5)));
    BufferedNotification leaderRow = row(leaderId, leaderPriority, 10);
    BufferedNotification siblingRow = row(siblingId, siblingPriority, 5);

    transactions(flag).flushAggregate(List.of(leaderRow, siblingRow), leaderRow,
        new SummarizedContent("Update", "Two things happened.", suggested));

    ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxEventRepository).save(outbox.capture());
    return outbox.getValue();
  }

  private String leaderLogDetail() {
    ArgumentCaptor<NotificationLog> logs = ArgumentCaptor.forClass(NotificationLog.class);
    verify(notificationLogRepository, atLeastOnce()).save(logs.capture());
    return logs.getAllValues().stream()
        .filter(entry -> entry.getNotificationId().equals(leaderId))
        .map(NotificationLog::getDetails).findFirst().orElseThrow();
  }

  private Priority savedLeaderPriority() {
    ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, atLeastOnce()).save(saved.capture());
    return saved.getAllValues().stream().filter(n -> n.getId().equals(leaderId))
        .findFirst().orElseThrow().getPriority();
  }

  @Test
  void flagOn_modelRaisesPriority_isAppliedAndRoutedViaOutboxPayload() {
    OutboxEvent event = flush(true, Priority.MEDIUM, Priority.LOW, Priority.HIGH);

    assertEquals("HIGH", event.getPayload().get("priority"));
    assertEquals(Priority.HIGH, savedLeaderPriority());
  }

  @Test
  void flagOn_recordsSuggestedAndAppliedPriorityInTheLeaderLog() {
    flush(true, Priority.MEDIUM, Priority.LOW, Priority.HIGH);

    String detail = leaderLogDetail();
    assertTrue(detail.contains("suggested HIGH"), detail);
    assertTrue(detail.contains("applied HIGH"), detail);
  }

  @Test
  void flagOn_modelTriesToLowerBelowHighestMember_isClampedToTheHighestMember() {
    OutboxEvent event = flush(true, Priority.MEDIUM, Priority.MEDIUM, Priority.LOW);

    assertEquals("MEDIUM", event.getPayload().get("priority"));
    assertEquals(Priority.MEDIUM, savedLeaderPriority());
    String detail = leaderLogDetail();
    assertTrue(detail.contains("suggested LOW"), detail);
    assertTrue(detail.contains("applied MEDIUM"), detail);
  }

  @Test
  void flagOn_leaderIsLowButSiblingIsMedium_modelLowStillRaisesToMedium() {
    OutboxEvent event = flush(true, Priority.LOW, Priority.MEDIUM, Priority.LOW);

    assertEquals("MEDIUM", event.getPayload().get("priority"));
    assertEquals(Priority.MEDIUM, savedLeaderPriority());
  }

  @Test
  void flagOn_noValidSuggestion_keepsTheLeaderPriorityAndSaysSo() {
    OutboxEvent event = flush(true, Priority.MEDIUM, Priority.LOW, null);

    assertEquals("MEDIUM", event.getPayload().get("priority"));
    assertEquals(Priority.MEDIUM, savedLeaderPriority());
    String detail = leaderLogDetail();
    assertTrue(detail.contains("no valid suggestion"), detail);
    assertTrue(detail.contains("applied MEDIUM"), detail);
  }

  @Test
  void flagOff_modelPriorityIsIgnoredAndLogIsUnchanged() {
    OutboxEvent event = flush(false, Priority.MEDIUM, Priority.LOW, Priority.HIGH);

    assertEquals("MEDIUM", event.getPayload().get("priority"));
    assertEquals(Priority.MEDIUM, savedLeaderPriority());
    assertFalse(leaderLogDetail().toLowerCase().contains("priority"));
  }
}
