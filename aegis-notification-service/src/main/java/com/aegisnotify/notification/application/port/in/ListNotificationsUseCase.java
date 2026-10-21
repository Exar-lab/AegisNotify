package com.aegisnotify.notification.application.port.in;

import com.aegisnotify.notification.application.dto.NotificationListQuery;
import com.aegisnotify.notification.application.dto.NotificationPage;
import com.aegisnotify.notification.application.dto.NotificationSummary;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import java.util.List;

public interface ListNotificationsUseCase {

  NotificationPage<NotificationSummary> listPage(
      NotificationListQuery query);

  default List<NotificationSummary> list(Channel channel, NotificationStatus status) {
    return list(channel, status, 20);
  }

  List<NotificationSummary> list(Channel channel, NotificationStatus status, Integer limit);
}
