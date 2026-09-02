package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.port.in.PublishOutboxEventUseCase;
import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a single outbox relay poll without holding one database
 * transaction open across the whole batch. Each event's publish-and-mark
 * work runs in its own transaction, owned by
 * {@link PublishOutboxEventTransactions}: a failure on event N rolls back
 * only event N (leaving it {@code UNPROCESSED} for retry) and never touches
 * events already committed as {@code PROCESSED} earlier in the batch, nor
 * blocks evaluation of events later in the batch.
 */
@Service
public class PublishOutboxEventService implements PublishOutboxEventUseCase {

  private static final Logger log = LoggerFactory.getLogger(PublishOutboxEventService.class);

  private final OutboxEventRepository outboxEventRepository;
  private final PublishOutboxEventTransactions transactions;

  public PublishOutboxEventService(OutboxEventRepository outboxEventRepository,
      PublishOutboxEventTransactions transactions) {
    this.outboxEventRepository = outboxEventRepository;
    this.transactions = transactions;
  }

  @Override
  public int publishPending() {
    List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents();
    int publishedCount = 0;

    for (OutboxEvent event : pendingEvents) {
      try {
        transactions.publishOne(event);
        publishedCount++;
      } catch (RuntimeException ex) {
        log.warn("outbox_event_publish_failed outboxEventId={} notificationId={} reason={}",
            event.getId(), event.getNotificationId(), ex.getMessage());
      }
    }

    return publishedCount;
  }
}
