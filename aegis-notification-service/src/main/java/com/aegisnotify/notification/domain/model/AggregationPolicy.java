package com.aegisnotify.notification.domain.model;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.Priority;

/**
 * Decides whether a notification is eligible for aggregation and, if so,
 * which group it belongs to (D1/D3/D12/D13). Zero Spring dependency — plain
 * domain logic, unit-testable without a container.
 */
public final class AggregationPolicy {

  private AggregationPolicy() {
  }

  /**
   * Determines whether a notification may be held for aggregation instead of
   * publishing immediately.
   *
   * <p>Bypass order (first match wins, all fail-safe toward immediate
   * individual delivery):</p>
   * <ol>
   *   <li>Aggregation globally disabled ({@link AggregationSettings#enabled()}
   *       is {@code false})</li>
   *   <li>{@link Priority#HIGH} always bypasses (D3)</li>
   *   <li>Blank/unresolvable template name is treated as excluded (fail-safe,
   *       D13)</li>
   *   <li>Template name or channel configured as excluded bypasses (D13)</li>
   * </ol>
   *
   * @param priority     the notification's priority
   * @param channel      the notification's channel
   * @param templateName the notification's template name; blank is treated
   *                     as excluded (fail-safe)
   * @param settings     the current aggregation configuration
   * @return {@code true} only when the notification must be held for
   *     aggregation instead of publishing immediately
   */
  public static boolean isAggregatable(Priority priority, Channel channel,
      String templateName, AggregationSettings settings) {
    if (!settings.enabled()) {
      return false;
    }
    if (priority == Priority.HIGH) {
      return false;
    }
    if (templateName == null || templateName.isBlank()) {
      return false;
    }
    String normalizedTemplateName = templateName.trim().toLowerCase();
    boolean templateExcluded = settings.excludedTemplates().stream()
        .anyMatch(excluded -> excluded != null
            && excluded.trim().toLowerCase().equals(normalizedTemplateName));
    if (templateExcluded) {
      return false;
    }
    return !settings.excludedChannels().contains(channel);
  }

  /**
   * Builds the grouping key a notification would belong to. Callers must
   * check {@link #isAggregatable} first; this method performs no bypass
   * checks of its own.
   *
   * @param channel      the notification's channel
   * @param recipient    the notification's recipient
   * @param templateName the notification's template name
   * @param settings     the current aggregation configuration
   * @return the group key — {@code templateName} is {@code null} when {@link
   *     AggregationSettings#requireSameTemplate()} is {@code false} (D12)
   */
  public static AggregationGroupKey groupKeyFor(Channel channel, String recipient,
      String templateName, AggregationSettings settings) {
    return AggregationGroupKey.of(channel, recipient, templateName, settings.requireSameTemplate());
  }
}
