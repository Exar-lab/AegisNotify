package com.aegisnotify.notification.infrastructure.config;

import com.aegisnotify.notification.domain.enums.Channel;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.context.annotation.Configuration;

/**
 * External configuration for the notification aggregation buffer (issue
 * #86), mirroring {@link NotificationKafkaProperties}'s compact-constructor
 * validation style. As with {@code NotificationKafkaProperties.Topics},
 * numeric/duration fields are always expected to be supplied by {@code
 * application.yml}'s {@code ${ENV_VAR:default}} convention — the compact
 * constructor only fail-fasts on invalid values, it does not silently
 * substitute defaults for primitives.
 *
 * <p>{@code excluded-templates}/{@code excluded-channels} (D13, Slice 3)
 * bind as comma-separated {@code ${ENV_VAR:default}} lists, same convention
 * as every other {@code notification.*} property. Matching against these
 * sets is trimmed/case-insensitive and fail-safe (blank/unresolvable
 * template name treated as excluded) — that logic lives entirely in {@link
 * com.aegisnotify.notification.domain.model.AggregationPolicy#isAggregatable},
 * so this record does no normalization of its own beyond null-safety
 * (X1).</p>
 *
 * @param enabled              whether aggregation is active
 * @param window               how long a group is held before being flushed
 * @param pollInterval         how often {@link com.aegisnotify.notification
 *     .infrastructure.scheduling.AggregationWindowScheduler} polls for
 *     expired windows
 * @param requireSameTemplate  whether grouping also requires a matching
 *                             template name (D12, default {@code true})
 * @param maxGroupSize         cap on notifications held in a single group
 * @param claimLease           how long a claimed group may stay {@code
 *                             CLAIMED} before being eligible for reclaim
 * @param maxAttempts          claim attempts allowed before a group is
 *                             forced onto individual delivery
 * @param excludedTemplates    template names that must always bypass
 *                             aggregation (D13); empty when unconfigured
 * @param excludedChannels     channels that must always bypass aggregation
 *                             (D13); empty when unconfigured
 */
@ConfigurationProperties(prefix = "notification.aggregation")
public record NotificationAggregationProperties(
    boolean enabled,
    Duration window,
    Duration pollInterval,
    boolean requireSameTemplate,
    int maxGroupSize,
    Duration claimLease,
    int maxAttempts,
    Set<String> excludedTemplates,
    Set<Channel> excludedChannels) {

  private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(5);
  private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(10);
  private static final Duration DEFAULT_CLAIM_LEASE = Duration.ofMinutes(2);
  private static final int DEFAULT_MAX_GROUP_SIZE = 20;
  private static final int DEFAULT_MAX_ATTEMPTS = 3;

  @ConstructorBinding
  public NotificationAggregationProperties {
    window = window == null ? DEFAULT_WINDOW : window;
    pollInterval = pollInterval == null ? DEFAULT_POLL_INTERVAL : pollInterval;
    claimLease = claimLease == null ? DEFAULT_CLAIM_LEASE : claimLease;
    excludedTemplates = excludedTemplates == null ? Set.of() : Set.copyOf(excludedTemplates);
    excludedChannels = excludedChannels == null ? Set.of() : Set.copyOf(excludedChannels);

    requirePositiveDuration(window, "notification.aggregation.window");
    requirePositiveDuration(pollInterval, "notification.aggregation.poll-interval");
    requirePositiveDuration(claimLease, "notification.aggregation.claim-lease");
    requirePositive(maxGroupSize, "notification.aggregation.max-group-size");
    requirePositive(maxAttempts, "notification.aggregation.max-attempts");
  }

  /**
   * Back-compat convenience constructor: pre-D13 7-arg shape, both exclusion
   * sets default to empty (i.e. "nothing configured as excluded") — avoids
   * touching every existing call site that predates Slice 3, same idiom
   * already established by {@code Notification.reconstitute}'s 11-arg
   * overload and {@code NotificationKafkaProperties.Topics}'s 6-arg
   * overload.
   */
  public NotificationAggregationProperties(boolean enabled, Duration window,
      Duration pollInterval, boolean requireSameTemplate, int maxGroupSize, Duration claimLease,
      int maxAttempts) {
    this(enabled, window, pollInterval, requireSameTemplate, maxGroupSize, claimLease,
        maxAttempts, Set.of(), Set.of());
  }

  /** Default-config convenience constructor: disabled, safe defaults throughout. */
  public NotificationAggregationProperties() {
    this(false, DEFAULT_WINDOW, DEFAULT_POLL_INTERVAL, true, DEFAULT_MAX_GROUP_SIZE,
        DEFAULT_CLAIM_LEASE, DEFAULT_MAX_ATTEMPTS, Set.of(), Set.of());
  }

  private static void requirePositiveDuration(Duration value, String propertyName) {
    if (value.isZero() || value.isNegative()) {
      throw new IllegalStateException(propertyName + " must be a positive duration");
    }
  }

  private static void requirePositive(int value, String propertyName) {
    if (value <= 0) {
      throw new IllegalStateException(propertyName + " must be greater than zero");
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(NotificationAggregationProperties.class)
  static class Registration {
  }
}
