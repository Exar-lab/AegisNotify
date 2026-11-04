package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.dto.SummarizedContent;
import com.aegisnotify.notification.application.port.out.AggregationBufferRepository;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.AggregationSettings;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(AggregationFlushTransactions.class);

  private final AggregationBufferRepository aggregationBufferRepository;
  private final NotificationRepository notificationRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final AuditEventPublisherPort auditEventPublisherPort;
  private final AggregationSettings settings;

  public AggregationFlushTransactions(AggregationBufferRepository aggregationBufferRepository,
      NotificationRepository notificationRepository,
      OutboxEventRepository outboxEventRepository,
      NotificationLogRepository notificationLogRepository,
      AuditEventPublisherPort auditEventPublisherPort,
      AggregationSettings settings) {
    this.settings = settings;
    this.aggregationBufferRepository = aggregationBufferRepository;
    this.notificationRepository = notificationRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.notificationLogRepository = notificationLogRepository;
    this.auditEventPublisherPort = auditEventPublisherPort;
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
   * Last-resort delivery for a claimed row whose {@link #flushIndividually}
   * kept failing past {@code max-attempts} (issue #225): instead of giving up
   * and dropping the notification, release it loose — write a fresh outbox
   * event carrying {@code skipAggregation: true}, so it is delivered as its
   * own notification and never re-held — and mark the buffer row {@code DONE}.
   * Runs in its own transaction and writes only the outbox event and the row
   * resolution (no trace log, see {@link #recordLooseRelease}), so a failure
   * confined to the log write cannot keep blocking delivery. If this fails
   * too the row is left {@code CLAIMED} and reclaimed after the lease; it is
   * never marked {@code DONE} without an outbox event or an explicit
   * {@code FAILED} log (missing notification).
   */
  @Transactional
  public void releaseLoose(BufferedNotification claimed, String failureReason) {
    Optional<Notification> notification =
        notificationRepository.findById(claimed.getNotificationId());

    notification.ifPresentOrElse(
        found -> outboxEventRepository.save(buildOutboxEvent(found)),
        () -> notificationLogRepository.save(NotificationLog.create(
            claimed.getNotificationId(), LogStatus.FAILED,
            "Aggregation buffer flush found no matching notification (after "
                + claimed.getAttempts() + " attempts: " + failureReason + ")")));

    aggregationBufferRepository.resolve(claimed.getId());
  }

  /**
   * Best-effort audit trace for {@link #releaseLoose}, in its own transaction
   * so a failing log write can never undo or block the release itself.
   */
  @Transactional
  public void recordLooseRelease(BufferedNotification claimed, String failureReason) {
    notificationLogRepository.save(NotificationLog.create(
        claimed.getNotificationId(), LogStatus.PENDING,
        AggregationLogMessages.RELEASED_INDIVIDUALLY_PREFIX + " after "
            + claimed.getAttempts() + " failed attempts (" + failureReason + ")"));
  }

  private void writeIndividualOutboxEvent(Notification notification) {
    outboxEventRepository.save(buildOutboxEvent(notification));

    notificationLogRepository.save(NotificationLog.create(notification.getId(),
        LogStatus.PENDING, AggregationLogMessages.RELEASED_INDIVIDUALLY_PREFIX));
  }

  /**
   * D5 audit fan-out helper: publishes one {@link AuditEventMessage} for a
   * single original notification folded into an aggregate, keyed by its own
   * {@code notificationId}. {@code details} is expected to already carry the
   * shared aggregation id (built by the caller) so a grepped/searched audit
   * log line correlates back to the aggregate send without any {@code
   * aegis-audit-service} schema change (X2/D5) — the message shape is
   * identical to every other {@link AuditEventMessage} published in this
   * service.
   */
  private void publishAggregationAuditEvent(Notification notification, String details) {
    auditEventPublisherPort.publish(new AuditEventMessage(
        notification.getId(),
        AuditStatusMapper.toAuditStatus(notification.getStatus()),
        details,
        notification.getChannel().name(),
        notification.getRecipient(),
        notification.getPriority().name(),
        Instant.now()
    ));
  }

  /**
   * Same publish as {@link #publishAggregationAuditEvent} but never lets a
   * publish failure escape (review-reliability WARNING fix) — used in the
   * member fan-out loop of {@link #flushAggregate} so one bad audit publish
   * can never abort resolving the remaining group members. {@link
   * AuditEventPublisherPort} is fire-and-forget by contract, but nothing
   * about the interface itself enforces that at compile time, so this is
   * defense-in-depth rather than reliance on a single concrete adapter's
   * behavior.
   */
  private void publishAggregationAuditEventSafely(Notification notification, String details) {
    try {
      publishAggregationAuditEvent(notification, details);
    } catch (Exception ex) {
      log.warn("Failed to publish aggregation audit event for notification {}: {}",
          notification.getId(), ex.getMessage(), ex);
    }
  }

  /**
   * Bug fix (discovered testing the Groq summarizer live, issue #86
   * follow-up): a notification resolved out of the aggregation buffer —
   * individually via {@link #flushIndividually} or as the leader via {@link
   * #flushAggregate} — gets a brand-new outbox event here so the normal
   * Kafka relay can still pick it up. The domain's aggregation-eligibility
   * check has no notion of "already went through the hold/resolve cycle once": it
   * only inspects the event's channel/priority/templateName, which are
   * unchanged. Without {@code skipAggregation}, that fresh event looked
   * identical to a brand-new notification to the relay's aggregation gate
   * and got held AGAIN on the very next poll — forever, since every
   * resolution path (including the next hold) writes another fresh event
   * with the same unchanged shape. {@code skipAggregation} marks this event
   * as already past that decision, so {@code PublishOutboxEventService}
   * publishes it unconditionally instead of re-evaluating it. Notifications
   * created fresh via {@code CreateNotificationService} (and retried via
   * {@code RetryFailedNotificationService}, where re-entering aggregation on
   * a genuinely new delivery attempt is correct) never set this flag.
   */
  private OutboxEvent buildOutboxEvent(Notification notification) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("id", notification.getId().toString());
    payload.put("channel", notification.getChannel().name());
    payload.put("recipient", notification.getRecipient());
    payload.put("templateName", notification.getTemplateName());
    payload.put("parameters", notification.getParameters());
    payload.put("priority", notification.getPriority().name());
    payload.put("skipAggregation", true);
    return OutboxEvent.create(notification.getId(), payload);
  }

  /**
   * Issue #227 safeguard: the model may only keep or raise the priority. The
   * floor is the highest priority among the group's members (the leader's own
   * priority is among them); without a valid suggestion the leader's own
   * priority is kept, still never below that floor. {@link Priority} declares
   * HIGH first, so a lower ordinal means a higher priority.
   */
  private static Priority appliedPriority(List<BufferedNotification> members,
      Priority leaderPriority, Priority suggested) {
    Priority floor = leaderPriority;
    for (BufferedNotification member : members) {
      floor = moreUrgent(floor, member.getPriority());
    }
    return moreUrgent(floor, suggested == null ? leaderPriority : suggested);
  }

  private static Priority moreUrgent(Priority first, Priority second) {
    return first.ordinal() <= second.ordinal() ? first : second;
  }

  private static String priorityDetail(Priority suggested, Priority applied) {
    return (suggested == null ? "priority: no valid suggestion" : "priority: suggested "
        + suggested) + ", applied " + applied;
  }

  /**
   * Aggregate-success half of the flush phase (B3, Slice 2/3): links every
   * group member to a new aggregation id and persists the summarized body on
   * the leader only (X2), writes exactly ONE outbox event carrying the
   * leader's own unchanged payload shape, and resolves every member's
   * buffer row to {@code DONE}.
   *
   * <p>D5 audit fan-out (Slice 3): every original notification folded into
   * this aggregate — leader included — gets its OWN {@link AuditEventMessage}
   * publish here, all sharing this call's {@code aggregationId} in the
   * free-text {@code details} field so the audit trail stays individually
   * traceable per notification even though only one outbox event (and,
   * later, one provider call) actually happens. This is IN ADDITION to,
   * not instead of, the leader's own existing audit event from the relay
   * publish path once its single outbox event is picked up — the same
   * multi-event-per-lifecycle pattern every other notification already has
   * (queued, processing, terminal outcome all publish separately).
   * {@link AuditEventPublisherPort} is fire-and-forget by contract, but
   * nothing about the interface enforces that at compile time, so every
   * publish here — leader included — goes through {@link
   * #publishAggregationAuditEventSafely}: an audit-publish failure must never
   * roll back this transaction and lose the outbox write (or the sibling
   * fan-out) that already durably owns the actual notification delivery.</p>
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
   * @param summary    the summarizer's output; its body and subject are
   *                   persisted on the leader ({@code aggregate_body},
   *                   {@code aggregate_subject})
   */
  @Transactional
  public void flushAggregate(List<BufferedNotification> members, BufferedNotification leaderRow,
      SummarizedContent summary) {
    UUID aggregationId = UUID.randomUUID();
    Notification leaderNotification = notificationRepository.findById(leaderRow.getNotificationId())
        .orElseThrow(() -> new IllegalStateException(
            "Aggregation leader notification not found: " + leaderRow.getNotificationId()));

    Notification aggregatedLeader =
        leaderNotification.markAggregated(aggregationId, summary.body(), summary.subject());
    String priorityDetail = null;
    if (settings.modelPriorityEnabled()) {
      Priority applied = appliedPriority(members, aggregatedLeader.getPriority(),
          summary.priority());
      priorityDetail = priorityDetail(summary.priority(), applied);
      aggregatedLeader = aggregatedLeader.withPriority(applied);
    }
    notificationRepository.save(aggregatedLeader);
    outboxEventRepository.save(buildOutboxEvent(aggregatedLeader));
    String leaderDetail =
        "Aggregated as leader of a " + members.size() + "-notification group (aggregation "
            + aggregationId + ")" + (priorityDetail == null ? "" : "; " + priorityDetail);
    notificationLogRepository.save(
        NotificationLog.create(leaderRow.getNotificationId(), LogStatus.PENDING, leaderDetail));
    aggregationBufferRepository.resolve(leaderRow.getId());
    publishAggregationAuditEventSafely(aggregatedLeader, leaderDetail);

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
      String siblingDetail = "Folded into aggregate led by " + leaderRow.getNotificationId()
          + " (aggregation " + aggregationId + ")";
      notificationRepository.findById(member.getNotificationId())
          .ifPresent(sibling -> {
            Notification updatedSibling =
                notificationRepository.save(sibling.markAggregated(aggregationId, null)
                    .markQueued());
            publishAggregationAuditEventSafely(updatedSibling, siblingDetail);
          });
      notificationLogRepository.save(NotificationLog.create(member.getNotificationId(),
          LogStatus.QUEUED, siblingDetail));
      aggregationBufferRepository.resolve(member.getId());
    }
  }
}
