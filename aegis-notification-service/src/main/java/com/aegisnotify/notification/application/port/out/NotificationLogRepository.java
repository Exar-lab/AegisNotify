package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.domain.model.NotificationLog;
import java.util.List;
import java.util.UUID;

public interface NotificationLogRepository {

  NotificationLog save(NotificationLog log);

  List<NotificationLog> findByNotificationIdOrderByCreatedAt(UUID notificationId);
}
