package com.aegisnotify.notification.infrastructure.messaging.kafka;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Kafka-based implementation of {@link AuditEventPublisherPort}.
 *
 * <p>Sends audit events to the configured topic using the notification ID as
 * the partition key to guarantee per-notification ordering. When a transaction
 * is active, the send is deferred to after commit via
 * {@link TransactionSynchronization#afterCommit()} to prevent ghost events on
 * rollback. When no transaction is active, the message is sent immediately.</p>
 *
 * <p>Fire-and-forget semantics: all Kafka failures are caught, logged at WARN,
 * and never propagated to the caller.</p>
 */
public class AuditEventPublisherKafkaAdapter implements AuditEventPublisherPort {

  private static final Logger log =
      LoggerFactory.getLogger(AuditEventPublisherKafkaAdapter.class);

  private final KafkaTemplate<String, AuditEventMessage> kafkaTemplate;
  private final String topic;

  public AuditEventPublisherKafkaAdapter(
      KafkaTemplate<String, AuditEventMessage> kafkaTemplate,
      String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  @Override
  public void publish(AuditEventMessage event) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              doSend(event);
            }
          }
      );
    } else {
      doSend(event);
    }
  }

  private void doSend(AuditEventMessage event) {
    try {
      kafkaTemplate.send(topic, event.notificationId().toString(), event);
    } catch (Exception ex) {
      log.warn("audit_event_publish_failed notificationId={} status={}",
          event.notificationId(), event.status(), ex);
    }
  }
}
