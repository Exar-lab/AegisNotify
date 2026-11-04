package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.AggregationInfo;
import com.aegisnotify.notification.application.dto.NotificationLogEntry;
import com.aegisnotify.notification.application.dto.NotificationStatusResponse;
import com.aegisnotify.notification.application.port.in.GetNotificationStatusUseCase;
import com.aegisnotify.notification.application.port.out.DeliveryAttemptRepository;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.domain.enums.AggregationRole;
import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.exception.NotificationNotFoundException;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetNotificationStatusService implements GetNotificationStatusUseCase {

  private final NotificationRepository notificationRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final DeliveryAttemptRepository deliveryAttemptRepository;

  public GetNotificationStatusService(NotificationRepository notificationRepository,
      NotificationLogRepository notificationLogRepository,
      DeliveryAttemptRepository deliveryAttemptRepository) {
    this.notificationRepository = notificationRepository;
    this.notificationLogRepository = notificationLogRepository;
    this.deliveryAttemptRepository = deliveryAttemptRepository;
  }

  @Override
  public NotificationStatusResponse getStatus(UUID notificationId) {
    Notification notification = notificationRepository.findById(notificationId)
        .orElseThrow(() -> new NotificationNotFoundException(notificationId));

    List<NotificationLog> logs =
        notificationLogRepository.findByNotificationIdOrderByCreatedAt(notificationId);

    List<NotificationLogEntry> auditTrail = logs.stream()
        .map(log -> new NotificationLogEntry(
            log.getStatus(),
            log.getDetails(),
            log.getCreatedAt()
        ))
        .toList();

    return new NotificationStatusResponse(
        notification.getId(),
        notification.getChannel(),
        notification.getRecipient(),
        notification.getTemplateName(),
        notification.getStatus(),
        notification.getCreatedAt(),
        notification.getUpdatedAt(),
        auditTrail,
        notification.getPriority(),
        notification.getId(),
        deliveryAttemptRepository.findByNotificationId(notificationId),
        resolveAggregation(notification, logs)
    );
  }

  /**
   * Derives the aggregation state on read (issue #225): a notification with an
   * {@code aggregationId} is the LEADER when it carries the summarized body
   * and a SIBLING otherwise; one without it that has an "individual delivery"
   * release log entry was released loose from the buffer.
   */
  private AggregationInfo resolveAggregation(Notification notification,
      List<NotificationLog> logs) {
    UUID aggregationId = notification.getAggregationId();
    if (aggregationId == null) {
      boolean releasedIndividually = logs.stream().anyMatch(entry ->
          entry.getStatus() == LogStatus.PENDING && entry.getDetails() != null
              && entry.getDetails().startsWith(
                  AggregationLogMessages.RELEASED_INDIVIDUALLY_PREFIX));
      return releasedIndividually ? AggregationInfo.releasedIndividually()
          : AggregationInfo.none();
    }

    List<Notification> group = notificationRepository.findByAggregationId(aggregationId);
    boolean leader = notification.getAggregateBody() != null;
    UUID leaderId = leader ? notification.getId() : group.stream()
        .filter(member -> member.getAggregateBody() != null)
        .map(Notification::getId)
        .findFirst()
        .orElse(null);
    return new AggregationInfo(leader ? AggregationRole.LEADER : AggregationRole.SIBLING,
        aggregationId, group.size(), leaderId);
  }
}
