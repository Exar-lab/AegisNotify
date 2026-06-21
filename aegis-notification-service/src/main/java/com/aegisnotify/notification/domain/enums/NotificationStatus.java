package com.aegisnotify.notification.domain.enums;

public enum NotificationStatus {
  PENDING,
  QUEUED,
  PROCESSING,
  SENT,
  SENT_VIA_FALLBACK,
  FAILED,
  FAILED_CRITICAL
}
