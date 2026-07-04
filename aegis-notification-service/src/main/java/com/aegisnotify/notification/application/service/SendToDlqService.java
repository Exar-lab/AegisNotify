package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.port.in.SendToDlqUseCase;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.exception.NotificationNotFoundException;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SendToDlqService implements SendToDlqUseCase {

  private final NotificationRepository notificationRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final DeadLetterQueuePort deadLetterQueuePort;
  private final AuditEventPublisherPort auditEventPublisherPort;

  public SendToDlqService(NotificationRepository notificationRepository,
      NotificationLogRepository notificationLogRepository,
      DeadLetterQueuePort deadLetterQueuePort,
      AuditEventPublisherPort auditEventPublisherPort) {
    this.notificationRepository = notificationRepository;
    this.notificationLogRepository = notificationLogRepository;
    this.deadLetterQueuePort = deadLetterQueuePort;
    this.auditEventPublisherPort = auditEventPublisherPort;
  }

  @Override
  @Transactional
  public NotificationResponse sendToDlq(UUID notificationId, String reason) {
    Notification notification = notificationRepository.findById(notificationId)
        .orElseThrow(() -> new NotificationNotFoundException(notificationId));

    Notification failedCritical = notification.markFailedCritical(reason);
    notificationRepository.save(failedCritical);

    notificationLogRepository.save(
        NotificationLog.create(notificationId, LogStatus.FAILED_CRITICAL,
            "Manually sent to DLQ: " + reason)
    );

    Map<String, Object> payload = new HashMap<>();
    payload.put("notificationId", notificationId.toString());
    payload.put("channel", notification.getChannel().name());
    payload.put("recipient", notification.getRecipient());
    payload.put("templateName", notification.getTemplateName());

    deadLetterQueuePort.sendToDlq(notificationId, payload, reason);

    auditEventPublisherPort.publish(new AuditEventMessage(
        failedCritical.getId(),
        AuditStatusMapper.toAuditStatus(failedCritical.getStatus()),
        "Sent to DLQ: " + reason,
        failedCritical.getChannel().name(),
        failedCritical.getRecipient(),
        failedCritical.getPriority().name(),
        Instant.now()
    ));

    return new NotificationResponse(failedCritical.getId(), failedCritical.getStatus());
  }
}
