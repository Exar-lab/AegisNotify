package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.domain.model.DeliveryAttempt;
import java.util.List;
import java.util.UUID;

public interface DeliveryAttemptRepository {

  void saveAll(UUID notificationId, List<DeliveryAttempt> attempts);

  List<DeliveryAttempt> findByNotificationId(UUID notificationId);
}
