package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.port.in.PublishOutboxEventUseCase;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import com.aegisnotify.notification.application.port.out.MessageBrokerPort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublishOutboxEventService implements PublishOutboxEventUseCase {

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

  public PublishOutboxEventService(OutboxEventRepository outboxEventRepository,
      MessageBrokerPort messageBrokerPort,
      NotificationLogRepository notificationLogRepository,
      NotificationRepository notificationRepository,
      AuditEventPublisherPort auditEventPublisherPort) {
    this.outboxEventRepository = outboxEventRepository;
    this.messageBrokerPort = messageBrokerPort;
    this.notificationLogRepository = notificationLogRepository;
    this.notificationRepository = notificationRepository;
    this.auditEventPublisherPort = auditEventPublisherPort;
  }

  @Override
  @Transactional
  public int publishPending() {
    List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents();

    for (OutboxEvent event : pendingEvents) {
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

    return pendingEvents.size();
  }
}
