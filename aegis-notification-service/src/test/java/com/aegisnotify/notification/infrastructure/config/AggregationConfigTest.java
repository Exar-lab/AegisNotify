package com.aegisnotify.notification.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisnotify.notification.domain.model.AggregationSettings;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link AggregationConfig}'s Spring-properties-to-domain-object
 * mapping (X3 of the design) with no Spring context — plain object
 * construction is sufficient to prove the mapping is correct.
 */
class AggregationConfigTest {

  @Test
  void aggregationSettings_mapsAllPropertiesOntoDomainValueObject() {
    NotificationAggregationProperties properties = new NotificationAggregationProperties(
        true, Duration.ofMinutes(7), Duration.ofSeconds(15), false, 42,
        Duration.ofMinutes(3), 5);

    AggregationSettings settings = new AggregationConfig().aggregationSettings(properties);

    assertTrue(settings.enabled());
    assertEquals(Duration.ofMinutes(7), settings.window());
    assertEquals(false, settings.requireSameTemplate());
    assertEquals(42, settings.maxGroupSize());
    assertEquals(Duration.ofMinutes(3), settings.lease());
    assertEquals(5, settings.maxAttempts());
    assertTrue(settings.excludedTemplates().isEmpty());
    assertTrue(settings.excludedChannels().isEmpty());
  }

  @Test
  void clock_producesUtcInstantCloseToNow() {
    Instant before = Instant.now();
    Instant clockInstant = new AggregationConfig().clock().instant();
    Instant after = Instant.now();

    assertNotNull(clockInstant);
    assertTrue(!clockInstant.isBefore(before) && !clockInstant.isAfter(after.plusSeconds(1)));
  }
}
