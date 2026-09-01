package com.aegisnotify.notification.infrastructure.scheduling;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.port.in.PublishOutboxEventUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link OutboxWorkerScheduler} (issue #32, K5 of the design).
 *
 * <p>Mirrors {@code KafkaNotificationConsumerTest}: plain Mockito, no Spring
 * context, verifying delegation and the per-tick exception guard directly by
 * invoking the scheduled method. Actual {@code @Scheduled} firing on a
 * configured interval is proven separately by
 * {@link OutboxWorkerSchedulerIntegrationTest}.</p>
 */
@ExtendWith(MockitoExtension.class)
class OutboxWorkerSchedulerTest {

  @Mock
  private PublishOutboxEventUseCase publishOutboxEventUseCase;

  private OutboxWorkerScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new OutboxWorkerScheduler(publishOutboxEventUseCase);
  }

  @Test
  void pollOutboxEvents_delegatesToPublishPending() {
    when(publishOutboxEventUseCase.publishPending()).thenReturn(3);

    scheduler.pollOutboxEvents();

    verify(publishOutboxEventUseCase).publishPending();
  }

  @Test
  void pollOutboxEvents_whenPublishPendingThrowsOnce_survivesAndTicksAgain() {
    doThrow(new RuntimeException("broker unavailable"))
        .doReturn(1)
        .when(publishOutboxEventUseCase).publishPending();

    scheduler.pollOutboxEvents();
    scheduler.pollOutboxEvents();

    verify(publishOutboxEventUseCase, times(2)).publishPending();
  }
}
