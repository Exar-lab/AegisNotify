package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.port.in.FlushAggregationWindowsUseCase;
import com.aegisnotify.notification.application.port.out.AggregationBufferRepository;
import com.aegisnotify.notification.application.port.out.NotificationMetricsPort;
import com.aegisnotify.notification.domain.model.AggregationSettings;
import com.aegisnotify.notification.domain.model.BufferedNotification;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a single aggregation-flush poll without holding one database
 * transaction open across the whole batch — the claim and resolve work for
 * each buffered row runs in its own transaction, owned by {@link
 * AggregationFlushTransactions}.
 *
 * <p>Slice 1 has no summarizer wired in yet: every successfully claimed row
 * is resolved via individual delivery ({@link
 * AggregationFlushTransactions#flushIndividually}). The aggregate-summary
 * branch is Slice 2 scope.</p>
 *
 * <p>Each claimed row's resolution is isolated in its own try/catch,
 * mirroring {@link PublishOutboxEventService}'s per-item isolation: a
 * failure resolving row N must never abort the rest of the batch. When a
 * row's flush attempt fails AND it has exceeded {@code maxAttempts} (B3
 * poison-group guard), it is forced to a terminal {@code DONE} state via
 * {@link AggregationFlushTransactions#forcePoisonRowDone} instead of being
 * left to loop {@code CLAIMED} -&gt; lease-expiry -&gt; reclaimed forever. A
 * row that fails but has NOT yet exceeded {@code maxAttempts} is simply left
 * {@code CLAIMED}; it becomes reclaimable again once its lease expires.</p>
 *
 * <p>{@link Clock} is injected (not {@code Instant.now()}) so window-expiry
 * boundary behavior is deterministically testable.</p>
 */
@Service
public class FlushAggregationWindowsService implements FlushAggregationWindowsUseCase {

  private static final Logger log = LoggerFactory.getLogger(FlushAggregationWindowsService.class);

  private final AggregationBufferRepository aggregationBufferRepository;
  private final AggregationFlushTransactions transactions;
  private final AggregationSettings settings;
  private final Clock clock;
  private final NotificationMetricsPort metrics;

  public FlushAggregationWindowsService(AggregationBufferRepository aggregationBufferRepository,
      AggregationFlushTransactions transactions, AggregationSettings settings, Clock clock,
      NotificationMetricsPort metrics) {
    this.aggregationBufferRepository = aggregationBufferRepository;
    this.transactions = transactions;
    this.settings = settings;
    this.clock = clock;
    this.metrics = metrics;
  }

  @Override
  public int flushExpiredWindows() {
    Instant now = clock.instant();
    Instant leaseCutoff = now.minus(settings.lease());

    List<BufferedNotification> claimable =
        aggregationBufferRepository.findClaimable(now, leaseCutoff);

    int resolvedCount = 0;
    for (BufferedNotification bufferedNotification : claimable) {
      Optional<BufferedNotification> claimed;
      try {
        claimed = transactions.claim(bufferedNotification, now);
      } catch (RuntimeException ex) {
        log.warn("aggregation_buffer_claim_failed bufferedNotificationId={} reason={}",
            bufferedNotification.getId(), ex.getMessage());
        metrics.recordAggregationFlushError("claim_failed");
        continue;
      }

      if (claimed.isEmpty()) {
        // Lost the single-claimant race to a concurrent poll — skip, the
        // winner is responsible for resolving this row.
        continue;
      }

      // Slice 1: no AggregationSummarizerPort exists yet, so every claimed
      // row — including poison groups past max-attempts — always resolves
      // via individual delivery. The summarizer branch and the max-attempts
      // bypass-summarizer behavior are Slice 2 scope.
      if (resolveClaimedRow(claimed.get())) {
        resolvedCount++;
      }
    }

    return resolvedCount;
  }

  private boolean resolveClaimedRow(BufferedNotification claimed) {
    try {
      transactions.flushIndividually(claimed);
      metrics.recordAggregationFlushSuccess();
      return true;
    } catch (RuntimeException ex) {
      log.warn("aggregation_buffer_flush_failed bufferedNotificationId={} attempts={} reason={}",
          claimed.getId(), claimed.getAttempts(), ex.getMessage());
      metrics.recordAggregationFlushError("flush_failed");

      if (claimed.hasExceededMaxAttempts(settings.maxAttempts())) {
        try {
          transactions.forcePoisonRowDone(claimed, ex.getMessage());
        } catch (RuntimeException forceEx) {
          log.warn("aggregation_buffer_force_poison_row_failed bufferedNotificationId={} "
              + "reason={}", claimed.getId(), forceEx.getMessage());
          metrics.recordAggregationFlushError("force_poison_row_failed");
        }
      }
      // Left CLAIMED when still under maxAttempts: reclaimable again once
      // its lease expires, giving the next poll another attempt.
      return false;
    }
  }
}
