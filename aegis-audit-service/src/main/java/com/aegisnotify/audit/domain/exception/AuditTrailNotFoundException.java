package com.aegisnotify.audit.domain.exception;

import java.util.UUID;

public final class AuditTrailNotFoundException extends DomainException {

  public AuditTrailNotFoundException(UUID notificationId) {
    super(String.format("Audit trail not found for notification: %s",
        notificationId));
  }
}
