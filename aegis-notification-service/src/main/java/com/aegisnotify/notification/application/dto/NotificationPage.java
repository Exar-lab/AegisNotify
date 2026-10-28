package com.aegisnotify.notification.application.dto;

import java.util.List;

public record NotificationPage<T>(List<T> content, long totalElements, int page, int size) {
  public NotificationPage {
    content = List.copyOf(content);
  }
}
