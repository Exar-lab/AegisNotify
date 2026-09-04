package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.port.in.PublishOutboxEventUseCase;
import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.AggregationPolicy;
import com.aegisnotify.notification.domain.model.AggregationSettings;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a single outbox relay poll without holding one database
 * transaction open across the whole batch. Each event's publish-or-hold work
 * runs in its own transaction, owned by {@link PublishOutboxEventTransactions}:
 * a failure on event N rolls back only event N (leaving it {@code
 * UNPROCESSED} for retry) and never touches events already committed earlier
 * in the batch, nor blocks evaluation of events later in the batch.
 *
 * <p>This class owns the single aggregation hold guard (B2 of the design,
 * issue #86): before delegating an event, it asks {@link AggregationPolicy}
 * whether the event is aggregatable. HIGH-priority and D13-excluded events
 * always take the unchanged immediate-publish path; everything else that
 * qualifies is instead handed to {@link
 * PublishOutboxEventTransactions#holdForAggregation}, which inserts a
 * buffer row and marks the outbox row {@code PROCESSED} in the same
 * transaction — never publishing to the broker.</p>
 */
@Service
public class PublishOutboxEventService implements PublishOutboxEventUseCase {

  private static final Logger log = LoggerFactory.getLogger(PublishOutboxEventService.class);

  private final OutboxEventRepository outboxEventRepository;
  private final PublishOutboxEventTransactions transactions;
  private final AggregationSettings aggregationSettings;

  public PublishOutboxEventService(OutboxEventRepository outboxEventRepository,
      PublishOutboxEventTransactions transactions, AggregationSettings aggregationSettings) {
    this.outboxEventRepository = outboxEventRepository;
    this.transactions = transactions;
    this.aggregationSettings = aggregationSettings;
  }

  @Override
  public int publishPending() {
    List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents();
    int processedCount = 0;

    for (OutboxEvent event : pendingEvents) {
      try {
        if (isAggregatable(event)) {
          transactions.holdForAggregation(event);
        } else {
          transactions.publishOne(event);
        }
        processedCount++;
      } catch (RuntimeException ex) {
        log.warn("outbox_event_publish_failed outboxEventId={} notificationId={} reason={}",
            event.getId(), event.getNotificationId(), ex.getMessage());
      }
    }

    return processedCount;
  }

  private boolean isAggregatable(OutboxEvent event) {
    // Cheap short-circuit before touching the payload at all: avoids any
    // parsing cost — and any assumption about payload shape — on the
    // overwhelmingly common case where aggregation is off.
    if (!aggregationSettings.enabled()) {
      return false;
    }

    Map<String, Object> payload = event.getPayload();
    String channelValue = (String) payload.get("channel");
    if (channelValue == null) {
      return false;
    }

    Priority priority = Priority.valueOf((String) payload.get("priority"));
    Channel channel = Channel.valueOf(channelValue);
    String templateName = (String) payload.get("templateName");
    return AggregationPolicy.isAggregatable(priority, channel, templateName, aggregationSettings);
  }
}
