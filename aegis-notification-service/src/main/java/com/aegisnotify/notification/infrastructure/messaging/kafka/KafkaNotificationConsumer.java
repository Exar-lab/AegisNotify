package com.aegisnotify.notification.infrastructure.messaging.kafka;

import com.aegisnotify.notification.application.dto.NotificationEvent;
import com.aegisnotify.notification.application.port.in.ConsumeNotificationEventUseCase;
import com.aegisnotify.notification.infrastructure.config.NotificationKafkaMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka inbound adapter for notification events.
 *
 * <p>Only active when {@code notification.kafka.consumer.enabled} is {@code true} —
 * see {@link com.aegisnotify.notification.infrastructure.config.KafkaConsumerConfig},
 * whose beans this listener depends on.</p>
 */
@Component
@ConditionalOnProperty(prefix = "notification.kafka.consumer", name = "enabled",
    havingValue = "true")
public class KafkaNotificationConsumer {

  private static final Logger log =
      LoggerFactory.getLogger(KafkaNotificationConsumer.class);

  private final ConsumeNotificationEventUseCase consumeNotificationEventUseCase;
  private final NotificationKafkaMetrics metrics;

  public KafkaNotificationConsumer(
      ConsumeNotificationEventUseCase consumeNotificationEventUseCase,
      NotificationKafkaMetrics metrics) {
    this.consumeNotificationEventUseCase = consumeNotificationEventUseCase;
    this.metrics = metrics;
  }

  @KafkaListener(
      topics = {
          "${notification.kafka.topics.high-priority}",
          "${notification.kafka.topics.medium-priority}",
          "${notification.kafka.topics.low-priority}"
      },
      groupId = "${notification.kafka.consumer.group-id}",
      containerFactory = "notificationKafkaListenerContainerFactory"
  )
  public void consume(ConsumerRecord<String, NotificationEvent> record,
      Acknowledgment acknowledgment) {
    log.info("notification_event_received topic={} key={} notificationId={}",
        record.topic(), record.key(), record.value().id());
    consumeNotificationEventUseCase.consume(record.value().id());
    metrics.recordSuccess(record.topic());
    acknowledgment.acknowledge();
  }
}
