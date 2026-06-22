package com.aegisnotify.notification.application.port.out;

import java.util.Map;
import java.util.UUID;

public interface DeadLetterQueuePort {

  void sendToDlq(UUID notificationId, Map<String, Object> payload, String reason);
}
