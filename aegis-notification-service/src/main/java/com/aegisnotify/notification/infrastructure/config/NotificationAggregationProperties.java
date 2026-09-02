package com.aegisnotify.notification.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.context.annotation.Configuration;

/**
 * External configuration for the notification aggregation buffer (issue
 * #86). Slice 1 binds only the core grouping/windowing fields; {@code
 * excluded-templates}/{@code excluded-channels} (D13) and {@code
 * summarizer.*} are deferred to Slice 3/Slice 2 respectively, mirroring
 * {@link NotificationKafkaProperties}'s compact-constructor validation
 * style. As with {@code NotificationKafkaProperties.Topics}, numeric/duration
 * fields are always expected to be supplied by {@code application.yml}'s
 * {@code ${ENV_VAR:default}} convention — the compact constructor only
 * fail-fasts on invalid values, it does not silently substitute defaults for
 * primitives.
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
 */
@ConfigurationProperties(prefix = "notification.aggregation")
public record NotificationAggregationProperties(
    boolean enabled,
    Duration window,
    Duration pollInterval,
    boolean requireSameTemplate,
    int maxGroupSize,
    Duration claimLease,
    int maxAttempts) {

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

    requirePositiveDuration(window, "notification.aggregation.window");
    requirePositiveDuration(pollInterval, "notification.aggregation.poll-interval");
    requirePositiveDuration(claimLease, "notification.aggregation.claim-lease");
    requirePositive(maxGroupSize, "notification.aggregation.max-group-size");
    requirePositive(maxAttempts, "notification.aggregation.max-attempts");
  }

  /** Default-config convenience constructor: disabled, safe defaults throughout. */
  public NotificationAggregationProperties() {
    this(false, DEFAULT_WINDOW, DEFAULT_POLL_INTERVAL, true, DEFAULT_MAX_GROUP_SIZE,
        DEFAULT_CLAIM_LEASE, DEFAULT_MAX_ATTEMPTS);
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
