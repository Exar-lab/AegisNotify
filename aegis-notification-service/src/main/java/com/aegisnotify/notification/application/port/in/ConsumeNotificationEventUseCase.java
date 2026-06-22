package com.aegisnotify.notification.application.port.in;

import java.util.UUID;

public interface ConsumeNotificationEventUseCase {

  void consume(UUID notificationId);
}
