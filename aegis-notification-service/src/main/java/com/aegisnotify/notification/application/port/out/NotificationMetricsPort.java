package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.Priority;

/**
 * Records notification-level metrics. Kept out of the application layer's
 * direct control over the metrics backend — infrastructure implements this
 * with whatever instrumentation library is in use (Micrometer today).
 */
public interface NotificationMetricsPort {

  /**
   * Records one accepted notification request.
   *
   * @param channel the notification channel
   * @param priority the notification priority
   */
  void recordRequest(Channel channel, Priority priority);
}
