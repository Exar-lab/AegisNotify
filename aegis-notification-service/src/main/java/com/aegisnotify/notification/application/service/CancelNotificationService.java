package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.port.in.CancelNotificationUseCase;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.exception.NotificationNotCancellableException;
import com.aegisnotify.notification.domain.exception.NotificationNotFoundException;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelNotificationService implements CancelNotificationUseCase {

  private final NotificationRepository notificationRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final AuditEventPublisherPort auditEventPublisherPort;

  public CancelNotificationService(NotificationRepository notificationRepository,
      NotificationLogRepository notificationLogRepository,
      AuditEventPublisherPort auditEventPublisherPort) {
    this.notificationRepository = notificationRepository;
    this.notificationLogRepository = notificationLogRepository;
    this.auditEventPublisherPort = auditEventPublisherPort;
  }

  @Override
  @Transactional
  public NotificationResponse cancel(UUID notificationId) {
    Notification notification = notificationRepository.findById(notificationId)
        .orElseThrow(() -> new NotificationNotFoundException(notificationId));

    if (!notification.canCancel()) {
      throw new NotificationNotCancellableException(notificationId, notification.getStatus());
    }

    Notification cancelled = notification.markCancelled();
    notificationRepository.save(cancelled);

    notificationLogRepository.save(
        NotificationLog.create(notificationId, LogStatus.CANCELLED, "Notification cancelled")
    );

    auditEventPublisherPort.publish(new AuditEventMessage(
        cancelled.getId(),
        AuditStatusMapper.toAuditStatus(cancelled.getStatus()),
        "Notification cancelled",
        cancelled.getChannel().name(),
        cancelled.getRecipient(),
        cancelled.getPriority().name(),
        Instant.now()
    ));

    return new NotificationResponse(cancelled.getId(), cancelled.getStatus());
  }
}
