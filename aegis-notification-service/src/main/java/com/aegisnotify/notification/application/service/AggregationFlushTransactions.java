package com.aegisnotify.notification.application.service;

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
import java.util.Map;
import java.util.Optional;
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
    Map<String, Object> payload = new HashMap<>();
    payload.put("id", notification.getId().toString());
    payload.put("channel", notification.getChannel().name());
    payload.put("recipient", notification.getRecipient());
    payload.put("templateName", notification.getTemplateName());
    payload.put("parameters", notification.getParameters());
    payload.put("priority", notification.getPriority().name());

    outboxEventRepository.save(OutboxEvent.create(notification.getId(), payload));

    notificationLogRepository.save(NotificationLog.create(notification.getId(),
        LogStatus.PENDING, "Released from aggregation buffer for individual delivery"));
  }
}
