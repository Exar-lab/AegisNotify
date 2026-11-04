package com.aegisnotify.notification.domain.model;

import com.aegisnotify.notification.domain.enums.Channel;
import java.time.Duration;
import java.util.Set;

/**
 * Pure domain value object carrying the aggregation feature's runtime
 * configuration (X3 of the design). Built by infrastructure ({@code
 * AggregationConfig}) from Spring-bound properties and handed to domain/
 * application code, which must never depend on Spring directly.
 *
 * @param enabled              whether aggregation is active at all
 * @param window               how long a group is held before being flushed
 * @param requireSameTemplate  whether grouping also requires a matching
 *                             template name (D12, default {@code true})
 * @param maxGroupSize         cap on notifications summarized into a single
 *                             aggregate; larger claimable groups are split
 *                             into chunks of at most this size at flush time
 * @param excludedTemplates    template names that must always bypass
 *                             aggregation (D13), trimmed/case-insensitive
 * @param excludedChannels     channels that must always bypass aggregation
 *                             (D13)
 * @param lease                how long a claimed group may stay {@code
 *                             CLAIMED} before being eligible for reclaim
 * @param maxAttempts          claim attempts tolerated before a row that keeps
 *                             failing individual release is released loose
 *                             as its own notification (poison-row guard)
 */
public record AggregationSettings(
    boolean enabled,
    Duration window,
    boolean requireSameTemplate,
    int maxGroupSize,
    Set<String> excludedTemplates,
    Set<Channel> excludedChannels,
    Duration lease,
    int maxAttempts) {

  public AggregationSettings {
    excludedTemplates = excludedTemplates == null ? Set.of() : Set.copyOf(excludedTemplates);
    excludedChannels = excludedChannels == null ? Set.of() : Set.copyOf(excludedChannels);
  }
}
