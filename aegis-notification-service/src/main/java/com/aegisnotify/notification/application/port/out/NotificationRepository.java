package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.application.dto.DashboardAggregate;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.model.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

  Notification save(Notification notification);

  Optional<Notification> findById(UUID id);

  List<Notification> findByStatus(NotificationStatus status);

  List<Notification> findByChannel(Channel channel);

  /**
   * Returns every notification sharing the given aggregation id (X2 of the
   * design, issue #86) — the leader and every sibling folded into the same
   * aggregate send. Used by the applyResult sibling-outcome propagation
   * (Slice 3) to find every notification that must receive the leader's
   * final delivery outcome.
   *
   * @param aggregationId the shared aggregation id
   * @return every notification carrying this aggregation id, leader included
   */
  List<Notification> findByAggregationId(UUID aggregationId);

  /**
   * Returns every notification matching the given optional filters. Both
   * arguments are nullable and independently optional: a {@code null} filter
   * is not applied. Passing {@code (null, null)} returns every notification.
   * Results are ordered newest-first by creation time.
   *
   * @param channel the channel to filter by, or {@code null} for any channel
   * @param status the status to filter by, or {@code null} for any status
   * @return the matching notifications, newest first
   */
  List<Notification> search(Channel channel, NotificationStatus status);

  /**
   * Aggregates every notification created at or after {@code since} into
   * dashboard KPI counts — total volume, terminal outcomes, and average
   * delivery latency for notifications that reached {@code SENT} or {@code
   * SENT_VIA_FALLBACK}. Used by {@code GetDashboardSummaryUseCase}; a
   * single-pass aggregation so the dashboard endpoint never loads full
   * notification rows just to count them.
   *
   * @param since the inclusive lower bound of the window
   * @return the aggregated counts for the window
   */
  DashboardAggregate aggregateSince(Instant since);
}
