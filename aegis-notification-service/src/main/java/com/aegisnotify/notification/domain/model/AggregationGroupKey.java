package com.aegisnotify.notification.domain.model;

import com.aegisnotify.notification.domain.enums.Channel;

/**
 * Identifies the aggregation buffer group a notification belongs to (D1/D12).
 *
 * <p>{@code templateName} is {@code null} when {@code
 * notification.aggregation.require-same-template} is {@code false} — grouping
 * is then keyed on channel + recipient alone. When {@code
 * require-same-template} is {@code true} (the default), {@code templateName}
 * is populated and two notifications only share a group if their templates
 * also match.</p>
 */
public record AggregationGroupKey(Channel channel, String recipient, String templateName) {

  /**
   * Canonical grouping-key construction, shared by {@link
   * AggregationPolicy#groupKeyFor} and {@link BufferedNotification#groupKey}
   * so both entry points agree on the exact same rule instead of each
   * duplicating it: {@code templateName} is nulled out when {@code
   * requireSameTemplate} is {@code false} (D12).
   *
   * <p>Neither caller has a production call site yet — this is
   * forward-looking for Slice 2, which will consume grouping when the
   * summarizer branch is wired in.</p>
   */
  public static AggregationGroupKey of(Channel channel, String recipient,
      String templateName, boolean requireSameTemplate) {
    return new AggregationGroupKey(channel, recipient, requireSameTemplate ? templateName : null);
  }
}
