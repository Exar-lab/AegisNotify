package com.aegisnotify.notification.application.port.in;

import com.aegisnotify.notification.application.dto.NotificationStatusResponse;
import java.util.UUID;

public interface GetNotificationStatusUseCase {

  NotificationStatusResponse getStatus(UUID notificationId);
}
