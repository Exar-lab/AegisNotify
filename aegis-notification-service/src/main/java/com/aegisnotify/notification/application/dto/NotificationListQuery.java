package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import java.time.Instant;

public record NotificationListQuery(
    String search, String recipient, Channel channel, Priority priority,
    NotificationStatus status, Instant from, Instant to, int page, int size) {

  public NotificationListQuery {
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException("Page must be nonnegative and size between 1 and 100");
    }
    if (from != null && to != null && !from.isBefore(to)) {
      throw new IllegalArgumentException("From must be before to");
    }
  }
}
