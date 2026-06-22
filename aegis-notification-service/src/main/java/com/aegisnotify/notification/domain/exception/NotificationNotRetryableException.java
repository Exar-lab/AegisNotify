package com.aegisnotify.notification.domain.exception;

import com.aegisnotify.notification.domain.enums.NotificationStatus;
import java.util.UUID;

public final class NotificationNotRetryableException extends DomainException {

  public NotificationNotRetryableException(UUID id, NotificationStatus currentStatus) {
    super(String.format("Notification %s cannot be retried in status %s", id, currentStatus));
  }
}
