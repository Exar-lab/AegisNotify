package com.aegisnotify.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.Notification;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Covers the aggregation-specific additions to {@link Notification} (issue
 * #86, Slice 1): {@code aggregationId}/{@code aggregateBody} fields and
 * {@link Notification#markAggregated}. See {@code NotificationTest} for the
 * pre-existing, unaffected behavior.
 */
class NotificationAggregationTest {

  @Test
  void create_defaultsAggregationFieldsToNull() {
    Notification notification = Notification.create(
        Channel.EMAIL, "user@example.com", "welcome", Map.of(), Priority.HIGH);

    assertNull(notification.getAggregationId());
    assertNull(notification.getAggregateBody());
  }

  @Test
  void markAggregated_setsAggregationIdAndBody() {
    Notification notification = Notification.create(
        Channel.EMAIL, "user@example.com", "welcome", Map.of(), Priority.MEDIUM);

    UUID aggregationId = UUID.randomUUID();
    Notification aggregated = notification.markAggregated(aggregationId, "Summary text");

    assertEquals(aggregationId, aggregated.getAggregationId());
    assertEquals("Summary text", aggregated.getAggregateBody());
    assertEquals(notification.getId(), aggregated.getId());
  }

  @Test
  void reconstitute_backCompatOverload_defaultsAggregationFieldsToNull() {
    Notification notification = Notification.reconstitute(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now());

    assertNull(notification.getAggregationId());
    assertNull(notification.getAggregateBody());
  }

  @Test
  void reconstitute_fullOverload_preservesAggregationFields() {
    UUID aggregationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH, NotificationStatus.QUEUED,
        null, null, Instant.now(), Instant.now(),
        aggregationId, "Summary");

    assertEquals(aggregationId, notification.getAggregationId());
    assertEquals("Summary", notification.getAggregateBody());
  }

  @Test
  void markQueued_preservesExistingAggregationFields() {
    UUID aggregationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now(),
        aggregationId, "Summary");

    Notification queued = notification.markQueued();

    assertEquals(aggregationId, queued.getAggregationId());
    assertEquals("Summary", queued.getAggregateBody());
  }
}
