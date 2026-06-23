package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.port.in.RetryFailedNotificationUseCase;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.exception.NotificationNotFoundException;
import com.aegisnotify.notification.domain.exception.NotificationNotRetryableException;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetryFailedNotificationService implements RetryFailedNotificationUseCase {

  private final NotificationRepository notificationRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final AuditEventPublisherPort auditEventPublisherPort;

  public RetryFailedNotificationService(NotificationRepository notificationRepository,
      NotificationLogRepository notificationLogRepository,
      OutboxEventRepository outboxEventRepository,
      AuditEventPublisherPort auditEventPublisherPort) {
    this.notificationRepository = notificationRepository;
    this.notificationLogRepository = notificationLogRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.auditEventPublisherPort = auditEventPublisherPort;
  }

  @Override
  @Transactional
  public NotificationResponse retry(UUID notificationId) {
    Notification notification = notificationRepository.findById(notificationId)
        .orElseThrow(() -> new NotificationNotFoundException(notificationId));

    if (!notification.canRetry()) {
      throw new NotificationNotRetryableException(notificationId, notification.getStatus());
    }

    Notification reset = notification.resetToPending();
    notificationRepository.save(reset);

    notificationLogRepository.save(
        NotificationLog.create(notificationId, LogStatus.PENDING, "Manual retry requested")
    );

    Map<String, Object> payload = new HashMap<>();
    payload.put("id", reset.getId().toString());
    payload.put("channel", reset.getChannel().name());
    payload.put("recipient", reset.getRecipient());
    payload.put("templateName", reset.getTemplateName());
    payload.put("parameters", reset.getParameters());
    payload.put("priority", reset.getPriority().name());

    outboxEventRepository.save(OutboxEvent.create(reset.getId(), payload));

    auditEventPublisherPort.publish(new AuditEventMessage(
        reset.getId(),
        AuditStatusMapper.toAuditStatus(reset.getStatus()),
        "Retry initiated",
        reset.getChannel().name(),
        reset.getRecipient(),
        reset.getPriority().name(),
        Instant.now()
    ));

    return new NotificationResponse(reset.getId(), reset.getStatus());
  }
}
