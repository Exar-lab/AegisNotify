package com.aegisnotify.notification.domain.exception;

import com.aegisnotify.notification.domain.enums.NotificationStatus;
import java.util.UUID;

public final class NotificationNotCancellableException extends DomainException {

  public NotificationNotCancellableException(UUID id, NotificationStatus currentStatus) {
    super(String.format("Notification %s cannot be cancelled in status %s", id, currentStatus));
  }
}
