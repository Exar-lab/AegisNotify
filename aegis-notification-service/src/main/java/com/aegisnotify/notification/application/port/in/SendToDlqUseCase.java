package com.aegisnotify.notification.application.port.in;

import com.aegisnotify.notification.application.dto.NotificationResponse;
import java.util.UUID;

public interface SendToDlqUseCase {

  NotificationResponse sendToDlq(UUID notificationId, String reason);
}
