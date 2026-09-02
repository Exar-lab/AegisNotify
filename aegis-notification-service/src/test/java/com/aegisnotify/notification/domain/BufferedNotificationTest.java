package com.aegisnotify.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisnotify.notification.domain.enums.AggregationBufferStatus;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.BufferedNotification;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BufferedNotificationTest {

  @Test
  void create_startsBufferedWithZeroAttempts() {
    Instant now = Instant.now();
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, now.plusSeconds(300), now);

    assertEquals(AggregationBufferStatus.BUFFERED, buffered.getStatus());
    assertEquals(0, buffered.getAttempts());
    assertEquals(now, buffered.getCreatedAt());
  }

  @Test
  void claim_transitionsToClaimedAndIncrementsAttempts() {
    Instant now = Instant.now();
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, now.plusSeconds(300), now);

    Instant claimTime = now.plusSeconds(301);
    BufferedNotification claimed = buffered.claim(claimTime);

    assertEquals(AggregationBufferStatus.CLAIMED, claimed.getStatus());
    assertEquals(claimTime, claimed.getClaimedAt());
    assertEquals(1, claimed.getAttempts());
  }

  @Test
  void resolve_transitionsToDone() {
    Instant now = Instant.now();
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, now.plusSeconds(300), now)
        .claim(now.plusSeconds(301));

    BufferedNotification resolved = buffered.resolve();

    assertEquals(AggregationBufferStatus.DONE, resolved.getStatus());
  }

  @Test
  void hasExceededMaxAttempts_belowLimit_returnsFalse() {
    Instant now = Instant.now();
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, now.plusSeconds(300), now)
        .claim(now.plusSeconds(1));

    assertFalse(buffered.hasExceededMaxAttempts(3));
  }

  @Test
  void hasExceededMaxAttempts_aboveLimit_returnsTrue() {
    Instant now = Instant.now();
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, now.plusSeconds(300), now);
    for (int i = 0; i < 4; i++) {
      buffered = buffered.claim(now.plusSeconds(i + 1));
    }

    assertTrue(buffered.hasExceededMaxAttempts(3));
  }

  @Test
  void groupKey_requireSameTemplateTrue_includesTemplate() {
    Instant now = Instant.now();
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, now.plusSeconds(300), now);

    assertEquals("welcome", buffered.groupKey(true).templateName());
  }

  @Test
  void groupKey_requireSameTemplateFalse_excludesTemplate() {
    Instant now = Instant.now();
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, now.plusSeconds(300), now);

    assertEquals(null, buffered.groupKey(false).templateName());
  }
}
