package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.domain.enums.NotificationStatus;

/**
 * Maps notification lifecycle states to audit status strings.
 *
 * <p>Standard mappings use the {@link NotificationStatus} enum name directly.
 * Provider-specific failure statuses (PROVIDER_A_FAIL, PROVIDER_B_FAIL) are
 * derived from the processing context, not from the enum, since they represent
 * intermediate audit states that do not exist in the notification domain.</p>
 */
public final class AuditStatusMapper {

  private AuditStatusMapper() {
  }

  /**
   * Maps a {@link NotificationStatus} to its audit status string.
   *
   * @param status the notification status
   * @return the audit status string matching the enum name
   */
  public static String toAuditStatus(NotificationStatus status) {
    return status.name();
  }

  /**
   * Returns the provider-specific failure audit status.
   *
   * @param isPrimaryProvider true if the primary provider failed, false if fallback
   * @return "PROVIDER_A_FAIL" or "PROVIDER_B_FAIL"
   */
  public static String toProviderFailStatus(boolean isPrimaryProvider) {
    return isPrimaryProvider ? "PROVIDER_A_FAIL" : "PROVIDER_B_FAIL";
  }
}
