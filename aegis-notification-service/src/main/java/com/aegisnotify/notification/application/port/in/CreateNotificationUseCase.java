package com.aegisnotify.notification.application.port.in;

import com.aegisnotify.notification.application.dto.CreateNotificationCommand;
import com.aegisnotify.notification.application.dto.NotificationResponse;

public interface CreateNotificationUseCase {

  NotificationResponse create(CreateNotificationCommand command);
}
