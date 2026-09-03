package com.aegisnotify.notification.infrastructure.persistence.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aegisnotify.notification.domain.enums.AggregationBufferStatus;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.BufferedNotification;
import com.aegisnotify.notification.infrastructure.persistence.entity.AggregationBufferJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Round-trip mapping coverage for {@link AggregationBufferPersistenceMapper}
 * (task 1.8). Plain object round-trip — mirrors {@code
 * TemplatePersistenceMapperTest}'s pattern; a full Testcontainers
 * {@code @DataJpaTest} exercising Flyway's {@code V3} migration + the real
 * JPQL queries requires Docker, unavailable in this sandbox (same
 * pre-existing limitation documented for the Slice 0a/0b Testcontainers
 * suites) — see {@code AggregationBufferRepositoryAdapterTest} for the
 * conditional-update wiring proof and apply-progress notes for what remains.
 */
class AggregationBufferPersistenceMapperTest {

  private final AggregationBufferPersistenceMapper mapper =
      new AggregationBufferPersistenceMapper();

  @Test
  void toJpaThenToDomain_roundTripsAllFields() {
    UUID id = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(300);
    Instant claimedAt = now.plusSeconds(310);

    BufferedNotification original = BufferedNotification.reconstitute(
        id, notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, AggregationBufferStatus.CLAIMED, expiresAt, claimedAt, 1, now);

    AggregationBufferJpaEntity entity = mapper.toJpa(original);
    BufferedNotification roundTripped = mapper.toDomain(entity);

    assertEquals(id, roundTripped.getId());
    assertEquals(notificationId, roundTripped.getNotificationId());
    assertEquals(Channel.EMAIL, roundTripped.getChannel());
    assertEquals("user@example.com", roundTripped.getRecipient());
    assertEquals("welcome", roundTripped.getTemplateName());
    assertEquals(Priority.MEDIUM, roundTripped.getPriority());
    assertEquals(AggregationBufferStatus.CLAIMED, roundTripped.getStatus());
    assertEquals(expiresAt, roundTripped.getExpiresAt());
    assertEquals(claimedAt, roundTripped.getClaimedAt());
    assertEquals(1, roundTripped.getAttempts());
    assertEquals(now, roundTripped.getCreatedAt());
  }

  @Test
  void toJpaThenToDomain_nullTemplateNameAndClaimedAt_preserved() {
    UUID id = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();
    Instant now = Instant.now();

    BufferedNotification original = BufferedNotification.create(
        notificationId, Channel.SMS, "+34600000000", null, Priority.LOW,
        now.plusSeconds(300), now);
    BufferedNotification withId = BufferedNotification.reconstitute(
        id, notificationId, Channel.SMS, "+34600000000", null, Priority.LOW,
        original.getStatus(), original.getExpiresAt(), null, 0, now);

    AggregationBufferJpaEntity entity = mapper.toJpa(withId);
    BufferedNotification roundTripped = mapper.toDomain(entity);

    assertNull(roundTripped.getTemplateName());
    assertNull(roundTripped.getClaimedAt());
  }
}
