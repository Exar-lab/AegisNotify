package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.SummarizedContent;
import com.aegisnotify.notification.application.port.out.AggregationBufferRepository;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.model.BufferedNotification;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the two short-lived transactions that bracket an aggregation flush
 * (B3 of the design): {@link #claim} marks a buffered row {@code CLAIMED}
 * with single-claimant semantics, and {@link #flushIndividually} resolves a
 * claimed row by writing a fresh, unchanged-shape outbox event — the
 * individual-delivery path Slice 1 always takes, since no summarizer exists
 * yet. Both halves are called with no transaction open in between, mirroring
 * {@link NotificationProcessingTransactions} and {@link
 * PublishOutboxEventTransactions}.
 */
@Service
public class AggregationFlushTransactions {

  private final AggregationBufferRepository aggregationBufferRepository;
  private final NotificationRepository notificationRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final NotificationLogRepository notificationLogRepository;

  public AggregationFlushTransactions(AggregationBufferRepository aggregationBufferRepository,
      NotificationRepository notificationRepository,
      OutboxEventRepository outboxEventRepository,
      NotificationLogRepository notificationLogRepository) {
    this.aggregationBufferRepository = aggregationBufferRepository;
    this.notificationRepository = notificationRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.notificationLogRepository = notificationLogRepository;
  }

  /**
   * Attempts to claim a buffered row. Returns empty when a concurrent
   * claimer already won the race (B3/single-claimant semantics) — the caller
   * must simply skip that row for this poll.
   */
  @Transactional
  public Optional<BufferedNotification> claim(BufferedNotification bufferedNotification,
      Instant claimedAt) {
    return aggregationBufferRepository.claim(bufferedNotification, claimedAt);
  }

  /**
   * Resolves a claimed buffered row via individual delivery: writes a new,
   * unchanged-shape outbox event (identical payload contract to {@code
   * CreateNotificationService}) for the underlying notification, then marks
   * the buffered row {@code DONE}. If the underlying notification can no
   * longer be found, the buffered row is still resolved to {@code DONE} —
   * never left permanently held.
   */
  @Transactional
  public void flushIndividually(BufferedNotification claimed) {
    Optional<Notification> notification =
        notificationRepository.findById(claimed.getNotificationId());

    notification.ifPresentOrElse(this::writeIndividualOutboxEvent,
        () -> notificationLogRepository.save(NotificationLog.create(
            claimed.getNotificationId(), LogStatus.FAILED,
            "Aggregation buffer flush found no matching notification")));

    aggregationBufferRepository.resolve(claimed.getId());
  }

  /**
   * Forces a claimed row that has exhausted its claim attempts into a
   * terminal {@code DONE} state after {@link #flushIndividually} itself
   * failed (B3 poison-group guard). Runs in its own transaction, independent
   * of the failed flush attempt, so a persistent DB failure on the normal
   * flush path still cannot leave a row permanently {@code CLAIMED} —
   * looping CLAIMED -&gt; lease-expiry -&gt; reclaimed forever past {@code
   * maxAttempts} is exactly what this guards against.
   */
  @Transactional
  public void forcePoisonRowDone(BufferedNotification claimed, String failureReason) {
    notificationLogRepository.save(NotificationLog.create(
        claimed.getNotificationId(), LogStatus.FAILED,
        "Aggregation buffer flush exceeded max attempts (" + claimed.getAttempts()
            + "); giving up after: " + failureReason));

    aggregationBufferRepository.resolve(claimed.getId());
  }

  private void writeIndividualOutboxEvent(Notification notification) {
    outboxEventRepository.save(buildOutboxEvent(notification));

    notificationLogRepository.save(NotificationLog.create(notification.getId(),
        LogStatus.PENDING, "Released from aggregation buffer for individual delivery"));
  }

  private OutboxEvent buildOutboxEvent(Notification notification) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("id", notification.getId().toString());
    payload.put("channel", notification.getChannel().name());
    payload.put("recipient", notification.getRecipient());
    payload.put("templateName", notification.getTemplateName());
    payload.put("parameters", notification.getParameters());
    payload.put("priority", notification.getPriority().name());
    return OutboxEvent.create(notification.getId(), payload);
  }

  /**
   * Aggregate-success half of the flush phase (B3, Slice 2): links every
   * group member to a new aggregation id and persists the summarized body on
   * the leader only (X2), writes exactly ONE outbox event carrying the
   * leader's own unchanged payload shape, and resolves every member's
   * buffer row to {@code DONE}. No audit fan-out here — the leader's outbox
   * event still triggers exactly one audit event through the existing relay
   * publish path, same as any other outbox write; per-sibling audit
   * correlation is explicitly Slice 3 scope.
   *
   * <p>Throws (uncaught, rolling back this transaction) if the leader
   * notification cannot be found — the caller ({@code
   * FlushAggregationWindowsService}) catches any exception from this method
   * and falls every member in {@code members} back to individual delivery,
   * so a missing leader never silently drops the group.</p>
   *
   * @param members    every buffered row being resolved by this aggregate,
   *                   leader included
   * @param leaderRow  the buffered row chosen as the group leader (oldest
   *                   successfully rendered member, X2)
   * @param summary    the summarizer's output; only {@link
   *                   SummarizedContent#body()} is persisted today — see
   *                   apply-progress for the subject-persistence gap (no
   *                   {@code aggregate_subject} column exists)
   */
  @Transactional
  public void flushAggregate(List<BufferedNotification> members, BufferedNotification leaderRow,
      SummarizedContent summary) {
    UUID aggregationId = UUID.randomUUID();
    Notification leaderNotification = notificationRepository.findById(leaderRow.getNotificationId())
        .orElseThrow(() -> new IllegalStateException(
            "Aggregation leader notification not found: " + leaderRow.getNotificationId()));

    Notification aggregatedLeader =
        leaderNotification.markAggregated(aggregationId, summary.body());
    notificationRepository.save(aggregatedLeader);
    outboxEventRepository.save(buildOutboxEvent(aggregatedLeader));
    notificationLogRepository.save(NotificationLog.create(leaderRow.getNotificationId(),
        LogStatus.PENDING,
        "Aggregated as leader of a " + members.size() + "-notification group"));
    aggregationBufferRepository.resolve(leaderRow.getId());

    for (BufferedNotification member : members) {
      if (member.getId().equals(leaderRow.getId())) {
        continue;
      }
      // Bug fix (review-readability, CRITICAL): a sibling never gets its own
      // outbox event (only the leader does — that is the entire point of
      // aggregation), so nothing in the normal Kafka -> consumer -> provider
      // pipeline ever advances its status. Left at markAggregated's own
      // PENDING, GET /status would report PENDING forever and canCancel()
      // would stay true forever for a notification already folded into a
      // delivered group.
      //
      // Chosen fix: reuse the existing QUEUED status (matching what the
      // leader itself becomes once its single outbox event is picked up by
      // PublishOutboxEventTransactions.publishOne) rather than introducing a
      // brand-new terminal status value. A new value (e.g.
      // SENT_VIA_AGGREGATE) was rejected here: notifications.status is
      // guarded by a SQL CHECK constraint (chk_notification_status, V1
      // migration) enumerating exact allowed values, so a new status would
      // require its own schema migration — out of bounds for this fix and
      // consistent with D6 of the design ("no new notification status").
      // QUEUED does not fully stop canCancel() (QUEUED is cancelable), but
      // it removes the "stuck PENDING forever" bug and bounds the
      // cancelable window to the same in-flight window the leader itself
      // has between its own outbox write and eventual delivery outcome.
      notificationRepository.findById(member.getNotificationId())
          .ifPresent(sibling -> notificationRepository.save(
              sibling.markAggregated(aggregationId, null).markQueued()));
      notificationLogRepository.save(NotificationLog.create(member.getNotificationId(),
          LogStatus.QUEUED, "Folded into aggregate led by " + leaderRow.getNotificationId()));
      aggregationBufferRepository.resolve(member.getId());
    }
  }
}
