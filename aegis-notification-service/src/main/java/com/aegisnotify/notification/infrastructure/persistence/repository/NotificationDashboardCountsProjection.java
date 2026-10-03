package com.aegisnotify.notification.infrastructure.persistence.repository;

/**
 * Status-bucketed counts for a time window, produced by {@link
 * SpringDataNotificationRepository#countByStatusSince}.
 */
public interface NotificationDashboardCountsProjection {

  long getTotalCount();

  long getSentCount();

  long getSentViaFallbackCount();

  long getFailedCriticalCount();
}
