package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.port.out.AggregationBufferRepository;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import com.aegisnotify.notification.application.port.out.MessageBrokerPort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.AggregationSettings;
import com.aegisnotify.notification.domain.model.BufferedNotification;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the single, short-lived database transaction that brackets publishing
 * one outbox event, so that {@link PublishOutboxEventService} can loop over a
 * whole batch without holding one shared transaction across every event.
 *
 * <p>Each call to {@link #publishOne(OutboxEvent)} is its own transaction: if
 * the Kafka publish (or anything else in this method) fails, only THIS
 * event's transaction rolls back, leaving its outbox row {@code UNPROCESSED}
 * for retry. Events already committed as {@code PROCESSED} earlier in the
 * same batch are unaffected, and events later in the batch are still
 * attempted. This mirrors {@link NotificationProcessingTransactions}' use of
 * a dedicated collaborator to keep transaction boundaries narrow and
 * explicit.</p>
 */
@Service
public class PublishOutboxEventTransactions {

  private static final Map<Priority, String> TOPIC_MAP = Map.of(
      Priority.HIGH, "high-priority-topic",
      Priority.MEDIUM, "medium-priority-topic",
      Priority.LOW, "low-priority-topic"
  );

  private final OutboxEventRepository outboxEventRepository;
  private final MessageBrokerPort messageBrokerPort;
  private final NotificationLogRepository notificationLogRepository;
  private final NotificationRepository notificationRepository;
  private final AuditEventPublisherPort auditEventPublisherPort;
  private final AggregationBufferRepository aggregationBufferRepository;
  private final AggregationSettings aggregationSettings;
  private final Clock clock;

  public PublishOutboxEventTransactions(OutboxEventRepository outboxEventRepository,
      MessageBrokerPort messageBrokerPort,
      NotificationLogRepository notificationLogRepository,
      NotificationRepository notificationRepository,
      AuditEventPublisherPort auditEventPublisherPort,
      AggregationBufferRepository aggregationBufferRepository,
      AggregationSettings aggregationSettings,
      Clock clock) {
    this.outboxEventRepository = outboxEventRepository;
    this.messageBrokerPort = messageBrokerPort;
    this.notificationLogRepository = notificationLogRepository;
    this.notificationRepository = notificationRepository;
    this.auditEventPublisherPort = auditEventPublisherPort;
    this.aggregationBufferRepository = aggregationBufferRepository;
    this.aggregationSettings = aggregationSettings;
    this.clock = clock;
  }

  @Transactional
  public void publishOne(OutboxEvent event) {
    String priority = (String) event.getPayload().get("priority");
    String topic = TOPIC_MAP.get(Priority.valueOf(priority));

    messageBrokerPort.publish(topic, event.getPayload());

    OutboxEvent processed = event.markProcessed();
    outboxEventRepository.save(processed);

    notificationLogRepository.save(
        NotificationLog.create(event.getNotificationId(), LogStatus.QUEUED,
            "Published to " + topic)
    );

    notificationRepository.findById(event.getNotificationId())
        .ifPresent(notification -> {
          Notification queued = notification.markQueued();
          notificationRepository.save(queued);

          auditEventPublisherPort.publish(new AuditEventMessage(
              queued.getId(),
              AuditStatusMapper.toAuditStatus(queued.getStatus()),
              "Published to " + topic,
              queued.getChannel().name(),
              queued.getRecipient(),
              queued.getPriority().name(),
              Instant.now()
          ));
        });
  }

  /**
   * Holds an aggregatable outbox event instead of publishing it (B2 of the
   * design, issue #86): inserts a {@code aggregation_buffer} row and marks
   * the outbox row {@code PROCESSED} — never calling {@link
   * MessageBrokerPort#publish}. The notification itself is left {@code
   * PENDING} (D6: no new notification status), with a {@code
   * NotificationLog} entry recording the hold. Same per-event transaction
   * discipline as {@link #publishOne}: this method's own transaction is the
   * only unit of work, independent of every other event in the batch.
   */
  @Transactional
  public void holdForAggregation(OutboxEvent event) {
    Map<String, Object> payload = event.getPayload();
    UUID notificationId = event.getNotificationId();
    Channel channel = Channel.valueOf((String) payload.get("channel"));
    String recipient = (String) payload.get("recipient");
    String templateName = (String) payload.get("templateName");
    Priority priority = Priority.valueOf((String) payload.get("priority"));

    Instant now = clock.instant();
    Instant expiresAt = now.plus(aggregationSettings.window());

    BufferedNotification bufferedNotification = BufferedNotification.create(
        notificationId, channel, recipient, templateName, priority, expiresAt, now);
    aggregationBufferRepository.save(bufferedNotification);

    OutboxEvent processed = event.markProcessed();
    outboxEventRepository.save(processed);

    notificationLogRepository.save(
        NotificationLog.create(notificationId, LogStatus.PENDING, "Held for aggregation")
    );
  }
}
