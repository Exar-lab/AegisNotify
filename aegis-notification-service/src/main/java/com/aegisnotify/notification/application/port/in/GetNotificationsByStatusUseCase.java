package com.aegisnotify.notification.application.port.in;

import com.aegisnotify.notification.application.dto.NotificationSummary;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import java.util.List;

public interface GetNotificationsByStatusUseCase {

  List<NotificationSummary> getByStatus(NotificationStatus status);
}
