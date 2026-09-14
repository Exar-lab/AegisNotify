package com.aegisnotify.notification.application.port.in;

import com.aegisnotify.notification.application.dto.NotificationSummary;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import java.util.List;

public interface ListNotificationsUseCase {

  /**
   * Returns every notification matching the given optional filters. Both
   * arguments are nullable and independently optional: a {@code null} filter
   * is not applied. Passing {@code (null, null)} returns every notification.
   *
   * @param channel the channel to filter by, or {@code null} for any channel
   * @param status the status to filter by, or {@code null} for any status
   * @return the matching notifications, newest first
   */
  List<NotificationSummary> list(Channel channel, NotificationStatus status);
}
