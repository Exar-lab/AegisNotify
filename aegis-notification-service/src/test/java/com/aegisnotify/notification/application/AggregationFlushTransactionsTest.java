package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
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

  @Mock
  private AuditEventPublisherPort auditEventPublisherPort;

  private AggregationFlushTransactions transactions;

  @BeforeEach
  void setUp() {
    transactions = new AggregationFlushTransactions(aggregationBufferRepository,
        notificationRepository, outboxEventRepository, notificationLogRepository,
        auditEventPublisherPort);
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

  /**
   * Task 3.3/3.4 (D5 fallback-path scenario): a group that falls back to
   * individual delivery (Slice 1's unchanged path) must keep its existing
   * single-notification audit event, unaffected by the D5 fan-out added to
   * {@link AggregationFlushTransactions#flushAggregate}. {@link
   * AggregationFlushTransactions#flushIndividually} itself publishes NO
   * audit event at all — the individual notification's own audit event
   * still comes later, unchanged, via {@code
   * PublishOutboxEventTransactions#publishOne} once its freshly-written
   * outbox event is picked up by the relay, exactly as it always has.
   */
  @Test
  void flushIndividually_neverPublishesAuditEvent_fallbackPathUnaffectedByD5FanOut() {
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

    verifyNoInteractions(auditEventPublisherPort);
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

  /**
   * Task 3.1 (D5): an aggregate of 5 originals must produce exactly 5 {@link
   * AuditEventMessage} publishes — one per original notification ID, leader
   * included — each carrying the SAME {@code aggregationId} somewhere in its
   * free-text {@code details} field so an operator grepping the audit log
   * can correlate all 5 back to one aggregate send.
   *
   * <p>Fix 5 (review-reliability WARNING): each fabricated member has a
   * DISTINCT recipient/channel so a bug that swapped/reused the wrong
   * notification's recipient or channel per member would be caught, not
   * just a distinct-ID count — every published event's recipient/channel is
   * asserted against THAT SPECIFIC member's own notification data.</p>
   */
  @Test
  void flushAggregate_success_publishesOneAuditEventPerMember_allSharingAggregationId() {
    Instant now = Instant.now();
    List<UUID> notificationIds = List.of(UUID.randomUUID(), UUID.randomUUID(),
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    List<Channel> channels =
        List.of(Channel.EMAIL, Channel.SMS, Channel.EMAIL, Channel.WHATSAPP, Channel.PUSH);
    List<String> recipients = List.of("member0@example.com", "+34600000001",
        "member2@example.com", "+34600000003", "device-token-4");

    List<Notification> notifications = new java.util.ArrayList<>();
    for (int i = 0; i < notificationIds.size(); i++) {
      notifications.add(Notification.reconstitute(
          notificationIds.get(i), channels.get(i), recipients.get(i), "welcome",
          Map.of("name", "Jane"), Priority.MEDIUM, NotificationStatus.PENDING,
          null, null, now.minusSeconds(10), now));
    }
    for (int i = 0; i < notificationIds.size(); i++) {
      when(notificationRepository.findById(notificationIds.get(i)))
          .thenReturn(Optional.of(notifications.get(i)));
    }
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SummarizedContent summary = new SummarizedContent("Update", "Five things happened.");
    List<BufferedNotification> rows = notificationIds.stream()
        .map(id -> bufferedRow(id, now, now.minusSeconds(10)))
        .toList();
    BufferedNotification leaderRow = rows.get(0);

    transactions.flushAggregate(rows, leaderRow, summary);

    ArgumentCaptor<AuditEventMessage> auditCaptor =
        ArgumentCaptor.forClass(AuditEventMessage.class);
    verify(auditEventPublisherPort, times(5)).publish(auditCaptor.capture());
    List<AuditEventMessage> published = auditCaptor.getAllValues();

    assertEquals(notificationIds.size(),
        published.stream().map(AuditEventMessage::notificationId).distinct().count());
    assertTrue(notificationIds.containsAll(
        published.stream().map(AuditEventMessage::notificationId).toList()));

    // Every published event's details must reference the SAME aggregation
    // id — extract it from the leader's own detail string and assert every
    // other event's details contains it too.
    String leaderDetails = published.stream()
        .filter(e -> e.notificationId().equals(leaderRow.getNotificationId()))
        .findFirst().orElseThrow().details();
    String aggregationIdFragment = leaderDetails.substring(leaderDetails.indexOf("aggregation "));
    assertTrue(published.stream().allMatch(e -> e.details().contains(aggregationIdFragment)));

    // Fix 5: each published event's recipient/channel must match THAT
    // SPECIFIC member's own notification data, never another member's.
    for (int i = 0; i < notificationIds.size(); i++) {
      UUID memberId = notificationIds.get(i);
      AuditEventMessage event = published.stream()
          .filter(e -> e.notificationId().equals(memberId))
          .findFirst().orElseThrow();
      assertEquals(recipients.get(i), event.recipient());
      assertEquals(channels.get(i).name(), event.channel());
    }
  }

  /**
   * Fix 6 (review-reliability WARNING): a synchronously-throwing {@link
   * AuditEventPublisherPort} implementation (a hypothetical one that
   * violates the port's fire-and-forget javadoc contract) must not abort
   * the member fan-out loop, and must not affect the throwing member's own
   * already-completed save/log or the leader's own delivery-critical outbox
   * event either.
   */
  @Test
  void flushAggregate_onePublishThrows_othersStillProcessed() {
    Instant now = Instant.now();
    UUID leaderNotificationId = UUID.randomUUID();
    UUID sibling1Id = UUID.randomUUID();
    UUID sibling2Id = UUID.randomUUID();

    Notification leaderNotification = Notification.reconstitute(
        leaderNotificationId, Channel.EMAIL, "leader@example.com", "welcome",
        Map.of("name", "Jane"), Priority.MEDIUM, NotificationStatus.PENDING,
        null, null, now.minusSeconds(10), now);
    Notification sibling1 = Notification.reconstitute(
        sibling1Id, Channel.EMAIL, "sibling1@example.com", "welcome",
        Map.of("name", "Jane"), Priority.MEDIUM, NotificationStatus.PENDING,
        null, null, now.minusSeconds(5), now);
    Notification sibling2 = Notification.reconstitute(
        sibling2Id, Channel.SMS, "+34600000002", "welcome",
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
    org.mockito.Mockito.doAnswer(invocation -> {
      AuditEventMessage event = invocation.getArgument(0);
      if (event.notificationId().equals(sibling1Id)) {
        throw new RuntimeException("boom - simulated synchronous publish failure");
      }
      return null;
    }).when(auditEventPublisherPort).publish(any(AuditEventMessage.class));

    SummarizedContent summary = new SummarizedContent("Update", "Two things happened.");
    BufferedNotification leaderRow = bufferedRow(leaderNotificationId, now, now.minusSeconds(10));
    BufferedNotification sibling1Row = bufferedRow(sibling1Id, now, now.minusSeconds(5));
    BufferedNotification sibling2Row = bufferedRow(sibling2Id, now, now.minusSeconds(3));

    org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> transactions.flushAggregate(
        List.of(leaderRow, sibling1Row, sibling2Row), leaderRow, summary));

    // The leader's delivery-critical outbox event is written regardless.
    verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
    // All 3 members (leader + 2 siblings) still get saved/logged/resolved
    // even though sibling1's audit publish threw.
    verify(notificationRepository, times(3)).save(any(Notification.class));
    verify(aggregationBufferRepository).resolve(leaderRow.getId());
    verify(aggregationBufferRepository).resolve(sibling1Row.getId());
    verify(aggregationBufferRepository).resolve(sibling2Row.getId());
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
