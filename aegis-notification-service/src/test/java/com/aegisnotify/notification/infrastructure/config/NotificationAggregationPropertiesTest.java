package com.aegisnotify.notification.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NotificationAggregationProperties}'s compact
 * constructor: default values, and fail-fast validation mirroring {@link
 * NotificationKafkaProperties}'s style.
 */
class NotificationAggregationPropertiesTest {

  @Test
  void defaultConstructor_disabledWithSafeDefaults() {
    NotificationAggregationProperties properties = new NotificationAggregationProperties();

    assertFalse(properties.enabled());
    assertEquals(Duration.ofMinutes(5), properties.window());
    assertEquals(Duration.ofSeconds(10), properties.pollInterval());
    assertEquals(Duration.ofMinutes(2), properties.claimLease());
    assertEquals(20, properties.maxGroupSize());
    assertEquals(3, properties.maxAttempts());
    assertEquals(true, properties.requireSameTemplate());
  }

  @Test
  void compactConstructor_nullWindow_defaultsToFiveMinutes() {
    NotificationAggregationProperties properties = new NotificationAggregationProperties(
        true, null, Duration.ofSeconds(10), true, 20, Duration.ofMinutes(2), 3);

    assertEquals(Duration.ofMinutes(5), properties.window());
  }

  @Test
  void compactConstructor_zeroWindow_throws() {
    assertThrows(IllegalStateException.class, () -> new NotificationAggregationProperties(
        true, Duration.ZERO, Duration.ofSeconds(10), true, 20, Duration.ofMinutes(2), 3));
  }

  @Test
  void compactConstructor_negativePollInterval_throws() {
    assertThrows(IllegalStateException.class, () -> new NotificationAggregationProperties(
        true, Duration.ofMinutes(5), Duration.ofSeconds(-1), true, 20,
        Duration.ofMinutes(2), 3));
  }

  @Test
  void compactConstructor_zeroMaxGroupSize_throws() {
    assertThrows(IllegalStateException.class, () -> new NotificationAggregationProperties(
        true, Duration.ofMinutes(5), Duration.ofSeconds(10), true, 0,
        Duration.ofMinutes(2), 3));
  }

  @Test
  void compactConstructor_zeroMaxAttempts_throws() {
    assertThrows(IllegalStateException.class, () -> new NotificationAggregationProperties(
        true, Duration.ofMinutes(5), Duration.ofSeconds(10), true, 20,
        Duration.ofMinutes(2), 0));
  }

  @Test
  void compactConstructor_zeroClaimLease_throws() {
    assertThrows(IllegalStateException.class, () -> new NotificationAggregationProperties(
        true, Duration.ofMinutes(5), Duration.ofSeconds(10), true, 20,
        Duration.ZERO, 3));
  }
}
