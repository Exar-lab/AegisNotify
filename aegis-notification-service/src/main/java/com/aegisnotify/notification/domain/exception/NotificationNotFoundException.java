package com.aegisnotify.notification.domain.exception;

import java.util.UUID;

public final class NotificationNotFoundException extends DomainException {

  public NotificationNotFoundException(UUID id) {
    super(String.format("Notification not found: %s", id));
  }
}
