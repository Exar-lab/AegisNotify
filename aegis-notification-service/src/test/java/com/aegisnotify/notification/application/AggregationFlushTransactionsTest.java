package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.SummarizedContent;
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
import java.util.List;
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

  private BufferedNotification bufferedRow(UUID notificationId, Instant now,
      Instant createdAt) {
    return BufferedNotification.create(notificationId, Channel.EMAIL, "user@example.com",
        "welcome", Priority.MEDIUM, now.plusSeconds(300), createdAt).claim(now);
  }

  /**
   * Aggregate-success half of B3's flush phase (Slice 2, task 2.13):
   * exactly one outbox event using the leader's payload, {@code
   * aggregation_id} set on every group member, {@code aggregate_body} set
   * on the leader ONLY, every buffer row resolved to {@code DONE}.
   */
  @Test
  void flushAggregate_success_writesOneOutboxEventForLeaderAndLinksAllMembers() {
    Instant now = Instant.now();
    UUID leaderNotificationId = UUID.randomUUID();
    UUID sibling1Id = UUID.randomUUID();
    UUID sibling2Id = UUID.randomUUID();

    Notification leaderNotification = Notification.reconstitute(
        leaderNotificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "Jane"), Priority.MEDIUM, NotificationStatus.PENDING,
        null, null, now.minusSeconds(10), now);
    Notification sibling1 = Notification.reconstitute(
        sibling1Id, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "Jane"), Priority.MEDIUM, NotificationStatus.PENDING,
        null, null, now.minusSeconds(5), now);
    Notification sibling2 = Notification.reconstitute(
        sibling2Id, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "Jane"), Priority.MEDIUM, NotificationStatus.PENDING,
        null, null, now.minusSeconds(3), now);

    when(notificationRepository.findById(leaderNotificationId))
        .thenReturn(Optional.of(leaderNotification));
    when(notificationRepository.findById(sibling1Id)).thenReturn(Optional.of(sibling1));
    when(notificationRepository.findById(sibling2Id)).thenReturn(Optional.of(sibling2));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SummarizedContent summary = new SummarizedContent("Update", "Two things happened.");
    BufferedNotification leaderRow = bufferedRow(leaderNotificationId, now, now.minusSeconds(10));
    BufferedNotification sibling1Row = bufferedRow(sibling1Id, now, now.minusSeconds(5));
    BufferedNotification sibling2Row = bufferedRow(sibling2Id, now, now.minusSeconds(3));

    transactions.flushAggregate(
        List.of(leaderRow, sibling1Row, sibling2Row), leaderRow, summary);

    ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxEventRepository, times(1)).save(outboxCaptor.capture());
    OutboxEvent savedEvent = outboxCaptor.getValue();
    assertEquals(leaderNotificationId, savedEvent.getNotificationId());
    assertEquals(leaderNotificationId.toString(), savedEvent.getPayload().get("id"));
    assertEquals("EMAIL", savedEvent.getPayload().get("channel"));
    assertEquals("welcome", savedEvent.getPayload().get("templateName"));

    ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(3)).save(notificationCaptor.capture());
    List<Notification> saved = notificationCaptor.getAllValues();

    Notification savedLeader = saved.stream()
        .filter(n -> n.getId().equals(leaderNotificationId)).findFirst().orElseThrow();
    assertNotNull(savedLeader.getAggregationId());
    assertEquals("Two things happened.", savedLeader.getAggregateBody());

    Notification savedSibling1 = saved.stream()
        .filter(n -> n.getId().equals(sibling1Id)).findFirst().orElseThrow();
    assertEquals(savedLeader.getAggregationId(), savedSibling1.getAggregationId());
    assertNull(savedSibling1.getAggregateBody());

    Notification savedSibling2 = saved.stream()
        .filter(n -> n.getId().equals(sibling2Id)).findFirst().orElseThrow();
    assertEquals(savedLeader.getAggregationId(), savedSibling2.getAggregationId());
    assertNull(savedSibling2.getAggregateBody());

    verify(aggregationBufferRepository).resolve(leaderRow.getId());
    verify(aggregationBufferRepository).resolve(sibling1Row.getId());
    verify(aggregationBufferRepository).resolve(sibling2Row.getId());
  }

  /**
   * Regression test (review-readability, CRITICAL): before this fix, {@code
   * markAggregated(id, null)} never touched {@code status}, so a sibling
   * left at its original {@code PENDING} would report PENDING forever via
   * {@code GET /status}, indistinguishable from a notification that was
   * never processed at all, even though it was actually resolved by the
   * leader's single outbox event.
   */
  @Test
  void flushAggregate_success_siblingIsNoLongerPending() {
    Instant now = Instant.now();
    UUID leaderNotificationId = UUID.randomUUID();
    UUID siblingId = UUID.randomUUID();

    Notification leaderNotification = Notification.reconstitute(
        leaderNotificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "Jane"), Priority.MEDIUM, NotificationStatus.PENDING,
        null, null, now.minusSeconds(10), now);
    Notification sibling = Notification.reconstitute(
        siblingId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "Jane"), Priority.MEDIUM, NotificationStatus.PENDING,
        null, null, now.minusSeconds(5), now);

    when(notificationRepository.findById(leaderNotificationId))
        .thenReturn(Optional.of(leaderNotification));
    when(notificationRepository.findById(siblingId)).thenReturn(Optional.of(sibling));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SummarizedContent summary = new SummarizedContent("Update", "One thing happened.");
    BufferedNotification leaderRow = bufferedRow(leaderNotificationId, now, now.minusSeconds(10));
    BufferedNotification siblingRow = bufferedRow(siblingId, now, now.minusSeconds(5));

    transactions.flushAggregate(List.of(leaderRow, siblingRow), leaderRow, summary);

    ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(2)).save(notificationCaptor.capture());
    Notification savedSibling = notificationCaptor.getAllValues().stream()
        .filter(n -> n.getId().equals(siblingId)).findFirst().orElseThrow();

    assertEquals(NotificationStatus.QUEUED, savedSibling.getStatus());
  }

  @Test
  void flushAggregate_leaderNotificationMissing_throwsAndTransactionCanRollBack() {
    Instant now = Instant.now();
    UUID leaderNotificationId = UUID.randomUUID();
    BufferedNotification leaderRow = BufferedNotification.create(
        leaderNotificationId, Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, now.plusSeconds(300), now).claim(now);

    when(notificationRepository.findById(leaderNotificationId)).thenReturn(Optional.empty());

    SummarizedContent summary = new SummarizedContent("Update", "body");

    org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
        () -> transactions.flushAggregate(List.of(leaderRow), leaderRow, summary));

    verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
  }
}
