package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.domain.model.BufferedNotification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for the durable {@code aggregation_buffer} table (B1 of the
 * design). Every write is a single committed handoff between durable owners
 * — a buffered notification is never held only in memory.
 */
public interface AggregationBufferRepository {

  BufferedNotification save(BufferedNotification bufferedNotification);

  /**
   * Returns every buffered notification eligible for claiming right now:
   * rows still {@code BUFFERED} whose window has expired ({@code expiresAt
   * <= now}), plus rows stuck {@code CLAIMED} past their lease ({@code
   * claimedAt <= leaseCutoff}) — the reclaim path for a crash between the
   * claim and flush phases (B3).
   */
  List<BufferedNotification> findClaimable(Instant now, Instant leaseCutoff);

  /**
   * Attempts to atomically transition {@code bufferedNotification} to {@code
   * CLAIMED}, guarded by a conditional update on the status it was read with
   * (single-claimant semantics, B3). Returns empty when another claimer won
   * the race first.
   *
   * @param bufferedNotification the row as read by the caller (its current
   *                             status is used as the compare-and-swap guard)
   * @param claimedAt            the instant to stamp as the claim time
   * @return the claimed row on success, empty if the claim lost the race
   */
  Optional<BufferedNotification> claim(BufferedNotification bufferedNotification,
      Instant claimedAt);

  /** Marks a buffered row {@code DONE} — its group has been flushed. */
  void resolve(UUID id);
}
