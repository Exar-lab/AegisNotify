package com.aegisnotify.notification.infrastructure.persistence.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.domain.enums.AggregationBufferStatus;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.BufferedNotification;
import com.aegisnotify.notification.infrastructure.persistence.mapper.AggregationBufferPersistenceMapper;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataAggregationBufferRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers {@link AggregationBufferRepositoryAdapter#claim}'s single-claimant
 * semantics (tasks 1.18/1.19): the conditional update is guarded by the
 * status the caller read, and a zero-row update (lost race) surfaces as
 * {@code Optional.empty()} rather than a false "claimed" result. A true
 * concurrent-thread proof requires a real database (Testcontainers,
 * unavailable in this sandbox) — this test proves the adapter's contract
 * with the underlying conditional-update primitive directly.
 */
@ExtendWith(MockitoExtension.class)
class AggregationBufferRepositoryAdapterTest {

  @Mock
  private SpringDataAggregationBufferRepository springDataRepository;

  private final AggregationBufferPersistenceMapper mapper =
      new AggregationBufferPersistenceMapper();

  private AggregationBufferRepositoryAdapter adapter() {
    return new AggregationBufferRepositoryAdapter(springDataRepository, mapper);
  }

  @Test
  void claim_conditionalUpdateSucceeds_returnsClaimedRow() {
    Instant now = Instant.now();
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, now.plusSeconds(300), now);

    when(springDataRepository.conditionalClaim(
        eq(buffered.getId()), eq(AggregationBufferStatus.BUFFERED),
        eq(AggregationBufferStatus.CLAIMED), eq(now), eq(1)))
        .thenReturn(1);

    Optional<BufferedNotification> result = adapter().claim(buffered, now);

    assertTrue(result.isPresent());
    assertEquals(AggregationBufferStatus.CLAIMED, result.get().getStatus());
    assertEquals(1, result.get().getAttempts());
  }

  @Test
  void claim_conditionalUpdateLosesRace_returnsEmpty() {
    Instant now = Instant.now();
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, now.plusSeconds(300), now);

    when(springDataRepository.conditionalClaim(any(), any(), any(), any(), eq(1)))
        .thenReturn(0);

    Optional<BufferedNotification> result = adapter().claim(buffered, now);

    assertTrue(result.isEmpty());
  }

  @Test
  void claim_staleClaimedRow_usesCurrentStatusAsGuard() {
    Instant now = Instant.now();
    BufferedNotification staleClaim = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, now.minusSeconds(600), now.minusSeconds(600))
        .claim(now.minusSeconds(300));

    when(springDataRepository.conditionalClaim(
        eq(staleClaim.getId()), eq(AggregationBufferStatus.CLAIMED),
        eq(AggregationBufferStatus.CLAIMED), eq(now), eq(2)))
        .thenReturn(1);

    Optional<BufferedNotification> result = adapter().claim(staleClaim, now);

    assertTrue(result.isPresent());
    assertEquals(2, result.get().getAttempts());
    verify(springDataRepository).conditionalClaim(staleClaim.getId(),
        AggregationBufferStatus.CLAIMED, AggregationBufferStatus.CLAIMED, now, 2);
  }

  @Test
  void resolve_delegatesToMarkDone() {
    UUID id = UUID.randomUUID();

    adapter().resolve(id);

    verify(springDataRepository).markDone(id);
  }
}
