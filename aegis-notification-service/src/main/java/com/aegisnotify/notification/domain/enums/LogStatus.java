package com.aegisnotify.notification.domain.enums;

public enum LogStatus {
  PENDING,
  QUEUED,
  PROCESSING,
  SENT,
  SENT_VIA_FALLBACK,
  PROVIDER_A_FAIL,
  PROVIDER_B_FAIL,
  FAILED,
  FAILED_CRITICAL
}
