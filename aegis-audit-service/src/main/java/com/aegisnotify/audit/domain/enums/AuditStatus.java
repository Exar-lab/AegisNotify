package com.aegisnotify.audit.domain.enums;

public enum AuditStatus {
  PENDING,
  QUEUED,
  PROCESSING,
  SENT,
  SENT_VIA_FALLBACK,
  PROVIDER_A_FAIL,
  PROVIDER_B_FAIL,
  FAILED,
  FAILED_CRITICAL,
  CANCELLED
}
