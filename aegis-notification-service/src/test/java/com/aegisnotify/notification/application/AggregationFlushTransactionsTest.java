package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.port.out.AggregationBufferRepository;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.application.service.AggregationFlushTransactions;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.BufferedNotification;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import java.time.Instant;
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
 * Covers {@link AggregationFlushTransactions}'s two transactional halves
 * (B3 of the design). Slice 1 has no summarizer, so {@link
 * AggregationFlushTransactions#flushIndividually} always writes an
 * unchanged-shape outbox event per notification (D-nothing-dropped
 * guarantee).
 */
@ExtendWith(MockitoExtension.class)
class AggregationFlushTransactionsTest {

  @Mock
  private AggregationBufferRepository aggregationBufferRepository;

  @Mock
  private NotificationRepository notificationRepository;

  @Mock
  private OutboxEventRepository outboxEventRepository;

  @Mock
  private NotificationLogRepository notificationLogRepository;

  private AggregationFlushTransactions transactions;

  @BeforeEach
  void setUp() {
    transactions = new AggregationFlushTransactions(aggregationBufferRepository,
        notificationRepository, outboxEventRepository, notificationLogRepository);
  }

  @Test
  void claim_delegatesToRepositoryClaim() {
    UUID notificationId = UUID.randomUUID();
    Instant now = Instant.now();
    BufferedNotification buffered = BufferedNotification.create(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, now.plusSeconds(300), now);
    BufferedNotification claimedResult = buffered.claim(now);

    when(aggregationBufferRepository.claim(buffered, now)).thenReturn(Optional.of(claimedResult));

    Optional<BufferedNotification> result = transactions.claim(buffered, now);

    assertTrue(result.isPresent());
    verify(aggregationBufferRepository).claim(buffered, now);
  }

  @Test
  void claim_lostRace_returnsEmpty() {
    UUID notificationId = UUID.randomUUID();
    Instant now = Instant.now();
    BufferedNotification buffered = BufferedNotification.create(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, now.plusSeconds(300), now);

    when(aggregationBufferRepository.claim(buffered, now)).thenReturn(Optional.empty());

    Optional<BufferedNotification> result = transactions.claim(buffered, now);

    assertTrue(result.isEmpty());
  }

  @Test
  void flushIndividually_writesUnchangedShapeOutboxEventAndResolvesBuffer() {
    UUID notificationId = UUID.randomUUID();
    Instant now = Instant.now();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "Jane"), Priority.MEDIUM, NotificationStatus.PENDING,
        null, null, now, now);
    final BufferedNotification claimed = BufferedNotification.create(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, now.plusSeconds(300), now).claim(now);

    when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
    when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    transactions.flushIndividually(claimed);

    ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxEventRepository).save(outboxCaptor.capture());
    OutboxEvent savedEvent = outboxCaptor.getValue();
    assertEquals(notificationId, savedEvent.getNotificationId());
    assertEquals(notificationId.toString(), savedEvent.getPayload().get("id"));
    assertEquals("EMAIL", savedEvent.getPayload().get("channel"));
    assertEquals("user@example.com", savedEvent.getPayload().get("recipient"));
    assertEquals("welcome", savedEvent.getPayload().get("templateName"));
    assertEquals("MEDIUM", savedEvent.getPayload().get("priority"));

    verify(aggregationBufferRepository).resolve(claimed.getId());
  }

  @Test
  void flushIndividually_notificationMissing_stillResolvesBuffer_neverDropsSilently() {
    UUID notificationId = UUID.randomUUID();
    Instant now = Instant.now();
    BufferedNotification claimed = BufferedNotification.create(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, now.plusSeconds(300), now).claim(now);

    when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    transactions.flushIndividually(claimed);

    verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    verify(aggregationBufferRepository).resolve(claimed.getId());
    verify(notificationLogRepository).save(any(NotificationLog.class));
  }
}
