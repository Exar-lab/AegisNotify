package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.port.in.SendToDlqUseCase;
import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.exception.NotificationNotFoundException;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
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

  public SendToDlqService(NotificationRepository notificationRepository,
      NotificationLogRepository notificationLogRepository,
      DeadLetterQueuePort deadLetterQueuePort) {
    this.notificationRepository = notificationRepository;
    this.notificationLogRepository = notificationLogRepository;
    this.deadLetterQueuePort = deadLetterQueuePort;
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

    return new NotificationResponse(failedCritical.getId(), failedCritical.getStatus());
  }
}
