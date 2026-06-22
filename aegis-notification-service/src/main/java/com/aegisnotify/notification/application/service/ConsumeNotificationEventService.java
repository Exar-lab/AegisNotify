package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.port.in.ConsumeNotificationEventUseCase;
import com.aegisnotify.notification.application.port.in.ProcessNotificationUseCase;
import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.model.NotificationLog;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConsumeNotificationEventService implements ConsumeNotificationEventUseCase {

  private final ProcessNotificationUseCase processNotificationUseCase;
  private final DeadLetterQueuePort deadLetterQueuePort;
  private final NotificationLogRepository notificationLogRepository;

  public ConsumeNotificationEventService(
      ProcessNotificationUseCase processNotificationUseCase,
      DeadLetterQueuePort deadLetterQueuePort,
      NotificationLogRepository notificationLogRepository) {
    this.processNotificationUseCase = processNotificationUseCase;
    this.deadLetterQueuePort = deadLetterQueuePort;
    this.notificationLogRepository = notificationLogRepository;
  }

  @Override
  public void consume(UUID notificationId) {
    try {
      NotificationResponse result = processNotificationUseCase.process(notificationId);

      if (result.status() == NotificationStatus.FAILED_CRITICAL) {
        deadLetterQueuePort.sendToDlq(notificationId,
            Map.of("notificationId", notificationId.toString()),
            "Critical failure after processing");
      }
    } catch (Exception exception) {
      notificationLogRepository.save(
          NotificationLog.create(notificationId, LogStatus.FAILED_CRITICAL,
              "Unexpected error: " + exception.getMessage())
      );

      deadLetterQueuePort.sendToDlq(notificationId,
          Map.of("notificationId", notificationId.toString()),
          "Unexpected error: " + exception.getMessage());
    }
  }
}
