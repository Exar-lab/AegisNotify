package com.aegisnotify.notification.infrastructure.scheduling;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.port.in.FlushAggregationWindowsUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AggregationWindowScheduler} (issue #86, Slice 1).
 * Mirrors {@code OutboxWorkerSchedulerTest} exactly: plain Mockito, no
 * Spring context, verifying delegation and the per-tick exception guard.
 */
@ExtendWith(MockitoExtension.class)
class AggregationWindowSchedulerTest {

  @Mock
  private FlushAggregationWindowsUseCase flushAggregationWindowsUseCase;

  private AggregationWindowScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new AggregationWindowScheduler(flushAggregationWindowsUseCase);
  }

  @Test
  void flushExpiredWindows_delegatesToUseCase() {
    when(flushAggregationWindowsUseCase.flushExpiredWindows()).thenReturn(3);

    scheduler.flushExpiredWindows();

    verify(flushAggregationWindowsUseCase).flushExpiredWindows();
  }

  @Test
  void flushExpiredWindows_whenUseCaseThrowsOnce_survivesAndTicksAgain() {
    doThrow(new RuntimeException("db unavailable"))
        .doReturn(1)
        .when(flushAggregationWindowsUseCase).flushExpiredWindows();

    scheduler.flushExpiredWindows();
    scheduler.flushExpiredWindows();

    verify(flushAggregationWindowsUseCase, times(2)).flushExpiredWindows();
  }
}
