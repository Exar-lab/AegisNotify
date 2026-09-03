package com.aegisnotify.notification.infrastructure.scheduling;

import com.aegisnotify.notification.application.port.in.FlushAggregationWindowsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the aggregation buffer for expired windows and stale claims,
 * resolving each via {@link FlushAggregationWindowsUseCase#flushExpiredWindows()}
 * (issue #86, Slice 1). Mirrors {@link OutboxWorkerScheduler}'s established
 * shape exactly: no {@code @Transactional} on the scheduler itself (the use
 * case's own collaborator, {@code AggregationFlushTransactions}, owns the
 * per-row transaction boundaries), and a per-tick exception guard so one
 * failing poll never kills the scheduled task for the JVM's lifetime.
 *
 * <p>Only active when {@code notification.aggregation.enabled} is {@code
 * true}. Scheduling infrastructure itself is bootstrapped unconditionally by
 * {@link com.aegisnotify.notification.infrastructure.config.SchedulingConfig}
 * (K7); only this individual task is gated, coexisting independently with
 * {@link OutboxWorkerScheduler}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "notification.aggregation", name = "enabled",
    havingValue = "true")
public class AggregationWindowScheduler {

  private static final Logger log = LoggerFactory.getLogger(AggregationWindowScheduler.class);

  private final FlushAggregationWindowsUseCase flushAggregationWindowsUseCase;

  public AggregationWindowScheduler(FlushAggregationWindowsUseCase flushAggregationWindowsUseCase) {
    this.flushAggregationWindowsUseCase = flushAggregationWindowsUseCase;
  }

  @Scheduled(fixedDelayString = "${notification.aggregation.poll-interval:PT10S}")
  public void flushExpiredWindows() {
    try {
      int resolvedCount = flushAggregationWindowsUseCase.flushExpiredWindows();
      if (resolvedCount > 0) {
        log.info("aggregation_flush_tick_completed resolvedCount={}", resolvedCount);
      }
    } catch (RuntimeException ex) {
      log.warn("aggregation_flush_tick_failed reason={}", ex.getMessage(), ex);
    }
  }
}
