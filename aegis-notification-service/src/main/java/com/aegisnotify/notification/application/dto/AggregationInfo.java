package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.enums.AggregationRole;
import java.util.UUID;

/**
 * Aggregation state of a notification, exposed additively in {@link
 * NotificationStatusResponse}.
 *
 * @param role          what part the notification played
 * @param aggregationId shared id of the aggregate, for {@code LEADER} and
 *                      {@code SIBLING}; otherwise {@code null}
 * @param groupSize     number of notifications folded into the aggregate
 *                      (leader included), for {@code LEADER} and {@code
 *                      SIBLING}; otherwise {@code null}
 * @param leaderId      id of the leader notification, for {@code LEADER}
 *                      (itself) and {@code SIBLING}; otherwise {@code null}
 */
public record AggregationInfo(AggregationRole role, UUID aggregationId, Integer groupSize,
    UUID leaderId) {

  private static final AggregationInfo NONE = new AggregationInfo(AggregationRole.NONE,
      null, null, null);

  public static AggregationInfo none() {
    return NONE;
  }

  public static AggregationInfo releasedIndividually() {
    return new AggregationInfo(AggregationRole.RELEASED_INDIVIDUALLY, null, null, null);
  }
}
