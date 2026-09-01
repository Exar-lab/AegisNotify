package com.aegisnotify.notification.infrastructure.scheduling;

import com.aegisnotify.notification.application.port.in.PublishOutboxEventUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the transactional outbox and relays pending events to the message
 * broker (issue #32, K5 of the design).
 *
 * <p>Only active when {@code notification.kafka.relay.enabled} is
 * {@code true} — see {@link com.aegisnotify.notification.infrastructure
 * .config.NotificationKafkaProperties.Relay}, the flag this scheduler was
 * created to finally read. Scheduling infrastructure itself is bootstrapped
 * unconditionally by {@link com.aegisnotify.notification.infrastructure
 * .config.SchedulingConfig} (K7); only this individual task is gated.</p>
 *
 * <p>Deliberately carries NO {@code @Transactional}: {@link
 * PublishOutboxEventUseCase#publishPending()} already brackets each event in
 * its own transaction via {@code PublishOutboxEventTransactions}. Wrapping
 * this method in an outer transaction would reintroduce the batch-rollback
 * bug that split was designed to avoid (K3/K5).</p>
 *
 * <p>An uncaught exception from a {@code @Scheduled} method silently kills
 * that task for the rest of the JVM's lifetime, which is unacceptable for the
 * delivery relay — so each tick is guarded: a failure is logged and the next
 * tick still fires.</p>
 */
@Component
@ConditionalOnProperty(prefix = "notification.kafka.relay", name = "enabled",
    havingValue = "true")
public class OutboxWorkerScheduler {

  private static final Logger log = LoggerFactory.getLogger(OutboxWorkerScheduler.class);

  private final PublishOutboxEventUseCase publishOutboxEventUseCase;

  public OutboxWorkerScheduler(PublishOutboxEventUseCase publishOutboxEventUseCase) {
    this.publishOutboxEventUseCase = publishOutboxEventUseCase;
  }

  @Scheduled(fixedDelayString = "${notification.kafka.relay.poll-interval:PT5S}")
  public void pollOutboxEvents() {
    try {
      int publishedCount = publishOutboxEventUseCase.publishPending();
      if (publishedCount > 0) {
        log.info("outbox_relay_tick_completed publishedCount={}", publishedCount);
      }
    } catch (RuntimeException ex) {
      log.warn("outbox_relay_tick_failed reason={}", ex.getMessage(), ex);
    }
  }
}
