package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.port.out.AggregationBufferRepository;
import com.aegisnotify.notification.application.port.out.NotificationMetricsPort;
import com.aegisnotify.notification.application.service.AggregationFlushTransactions;
import com.aegisnotify.notification.application.service.FlushAggregationWindowsService;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.AggregationSettings;
import com.aegisnotify.notification.domain.model.BufferedNotification;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers {@link FlushAggregationWindowsService}'s orchestration: it delegates
 * claim/resolve work to {@link AggregationFlushTransactions} per row without
 * holding a shared transaction, computes the claimable window from an
 * injected {@link Clock} (so expiry boundary behavior is deterministic), and
 * — since Slice 1 has no summarizer — always resolves a successfully claimed
 * row via individual delivery.
 */
@ExtendWith(MockitoExtension.class)
class FlushAggregationWindowsServiceTest {

  @Mock
  private AggregationBufferRepository aggregationBufferRepository;

  @Mock
  private AggregationFlushTransactions transactions;

  @Mock
  private NotificationMetricsPort metrics;

  private static final Instant FIXED_NOW = Instant.parse("2026-09-01T12:00:00Z");
  private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
  private final AggregationSettings settings = new AggregationSettings(
      true, Duration.ofMinutes(5), true, 20, Set.of(), Set.of(), Duration.ofMinutes(2), 3);

  private FlushAggregationWindowsService service() {
    return new FlushAggregationWindowsService(aggregationBufferRepository, transactions,
        settings, clock, metrics);
  }

  @Test
  void flushExpiredWindows_computesClaimableWindowFromInjectedClock() {
    when(aggregationBufferRepository.findClaimable(any(), any())).thenReturn(List.of());

    service().flushExpiredWindows();

    Instant expectedLeaseCutoff = FIXED_NOW.minus(Duration.ofMinutes(2));
    verify(aggregationBufferRepository).findClaimable(eq(FIXED_NOW), eq(expectedLeaseCutoff));
  }

  @Test
  void flushExpiredWindows_noClaimableRows_returnsZero() {
    when(aggregationBufferRepository.findClaimable(any(), any())).thenReturn(List.of());

    int resolved = service().flushExpiredWindows();

    assertEquals(0, resolved);
    verify(transactions, never()).claim(any(), any());
  }

  @Test
  void flushExpiredWindows_claimableRow_claimedAndFlushedIndividually() {
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));
    BufferedNotification claimed = buffered.claim(FIXED_NOW);

    when(aggregationBufferRepository.findClaimable(any(), any())).thenReturn(List.of(buffered));
    when(transactions.claim(buffered, FIXED_NOW)).thenReturn(Optional.of(claimed));

    int resolved = service().flushExpiredWindows();

    assertEquals(1, resolved);
    verify(transactions).claim(buffered, FIXED_NOW);
    verify(transactions).flushIndividually(claimed);
  }

  @Test
  void flushExpiredWindows_lostClaimRace_skipsRow_doesNotFlush() {
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));

    when(aggregationBufferRepository.findClaimable(any(), any())).thenReturn(List.of(buffered));
    when(transactions.claim(buffered, FIXED_NOW)).thenReturn(Optional.empty());

    int resolved = service().flushExpiredWindows();

    assertEquals(0, resolved);
    verify(transactions, never()).flushIndividually(any());
  }

  @Test
  void flushExpiredWindows_multipleRows_eachIndependentlyClaimedAndFlushed() {
    BufferedNotification buffered1 = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "a@example.com", "welcome",
        Priority.MEDIUM, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));
    BufferedNotification buffered2 = BufferedNotification.create(
        UUID.randomUUID(), Channel.SMS, "+34600000000", "otp",
        Priority.LOW, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));
    BufferedNotification claimed1 = buffered1.claim(FIXED_NOW);
    BufferedNotification claimed2 = buffered2.claim(FIXED_NOW);

    when(aggregationBufferRepository.findClaimable(any(), any()))
        .thenReturn(List.of(buffered1, buffered2));
    when(transactions.claim(buffered1, FIXED_NOW)).thenReturn(Optional.of(claimed1));
    when(transactions.claim(buffered2, FIXED_NOW)).thenReturn(Optional.of(claimed2));

    int resolved = service().flushExpiredWindows();

    assertEquals(2, resolved);
    verify(transactions).flushIndividually(claimed1);
    verify(transactions).flushIndividually(claimed2);
    verify(transactions, times(2)).claim(any(), eq(FIXED_NOW));
  }

  /**
   * Poison-group guard (B3): Slice 1 has no summarizer to skip, so an
   * over-attempted row still resolves via individual delivery exactly like
   * any other claimed row — the guard's only behavioral effect (bypassing a
   * summarizer call) is Slice 2 scope.
   */
  @Test
  void flushExpiredWindows_rowPastMaxAttempts_stillFlushedIndividually_neverDropped() {
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));
    BufferedNotification pastMaxAttempts = buffered.claim(FIXED_NOW.minusSeconds(200))
        .claim(FIXED_NOW.minusSeconds(150))
        .claim(FIXED_NOW.minusSeconds(100))
        .claim(FIXED_NOW.minusSeconds(50));
    org.junit.jupiter.api.Assertions.assertTrue(pastMaxAttempts.hasExceededMaxAttempts(3));

    when(aggregationBufferRepository.findClaimable(any(), any()))
        .thenReturn(List.of(pastMaxAttempts));
    when(transactions.claim(pastMaxAttempts, FIXED_NOW))
        .thenReturn(Optional.of(pastMaxAttempts.claim(FIXED_NOW)));

    int resolved = service().flushExpiredWindows();

    assertEquals(1, resolved);
    verify(transactions).flushIndividually(any(BufferedNotification.class));
  }

  /**
   * A failure resolving one row must never abort the rest of the batch —
   * mirrors {@code PublishOutboxEventServiceTest}'s
   * {@code publishPending_middleEventFails_doesNotRollBackOrBlockOtherEvents}.
   */
  @Test
  void flushExpiredWindows_middleRowFlushFails_doesNotBlockOtherRows() {
    BufferedNotification buffered1 = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "a@example.com", "welcome",
        Priority.MEDIUM, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));
    BufferedNotification buffered2 = BufferedNotification.create(
        UUID.randomUUID(), Channel.SMS, "+34600000000", "otp",
        Priority.LOW, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));
    BufferedNotification buffered3 = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "c@example.com", "welcome",
        Priority.MEDIUM, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));
    BufferedNotification claimed1 = buffered1.claim(FIXED_NOW);
    BufferedNotification claimed2 = buffered2.claim(FIXED_NOW);
    BufferedNotification claimed3 = buffered3.claim(FIXED_NOW);

    when(aggregationBufferRepository.findClaimable(any(), any()))
        .thenReturn(List.of(buffered1, buffered2, buffered3));
    when(transactions.claim(buffered1, FIXED_NOW)).thenReturn(Optional.of(claimed1));
    when(transactions.claim(buffered2, FIXED_NOW)).thenReturn(Optional.of(claimed2));
    when(transactions.claim(buffered3, FIXED_NOW)).thenReturn(Optional.of(claimed3));
    // All three explicitly stubbed: under Mockito's strict-stubs default, a
    // void method with ANY stubbing defined throws PotentialStubbingProblem
    // on an unstubbed-argument call, rather than silently no-op'ing.
    org.mockito.Mockito.doNothing().when(transactions).flushIndividually(claimed1);
    org.mockito.Mockito.doThrow(new RuntimeException("db write failed"))
        .when(transactions).flushIndividually(claimed2);
    org.mockito.Mockito.doNothing().when(transactions).flushIndividually(claimed3);

    int resolved = service().flushExpiredWindows();

    assertEquals(2, resolved);
    verify(transactions).flushIndividually(claimed1);
    verify(transactions).flushIndividually(claimed2);
    verify(transactions).flushIndividually(claimed3);
    // Row 2's failure is under maxAttempts (1 <= 3): left CLAIMED for a
    // future reclaim attempt, never forced to a terminal state prematurely.
    verify(transactions, never()).forcePoisonRowDone(any(), any());
  }

  /**
   * A row whose flush attempt fails AND has exceeded maxAttempts must reach
   * a terminal DONE state instead of looping CLAIMED -> lease-expiry ->
   * reclaimed forever (B3 poison-group guard).
   */
  @Test
  void flushExpiredWindows_rowExceedsMaxAttemptsAndFlushFails_forcedToTerminalState() {
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));
    BufferedNotification pastMaxAttempts = buffered.claim(FIXED_NOW.minusSeconds(200))
        .claim(FIXED_NOW.minusSeconds(150))
        .claim(FIXED_NOW.minusSeconds(100))
        .claim(FIXED_NOW.minusSeconds(50));
    BufferedNotification reclaimed = pastMaxAttempts.claim(FIXED_NOW);
    org.junit.jupiter.api.Assertions.assertTrue(reclaimed.hasExceededMaxAttempts(3));

    when(aggregationBufferRepository.findClaimable(any(), any()))
        .thenReturn(List.of(pastMaxAttempts));
    when(transactions.claim(pastMaxAttempts, FIXED_NOW)).thenReturn(Optional.of(reclaimed));
    org.mockito.Mockito.doThrow(new RuntimeException("db write failed"))
        .when(transactions).flushIndividually(reclaimed);

    int resolved = service().flushExpiredWindows();

    assertEquals(0, resolved);
    verify(transactions).forcePoisonRowDone(eq(reclaimed), any());
  }
}
