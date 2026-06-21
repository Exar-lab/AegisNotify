package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.NotificationLogEntry;
import com.aegisnotify.notification.application.dto.NotificationStatusResponse;
import com.aegisnotify.notification.application.port.in.GetNotificationStatusUseCase;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
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

  public GetNotificationStatusService(NotificationRepository notificationRepository,
      NotificationLogRepository notificationLogRepository) {
    this.notificationRepository = notificationRepository;
    this.notificationLogRepository = notificationLogRepository;
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
        auditTrail
    );
  }
}
