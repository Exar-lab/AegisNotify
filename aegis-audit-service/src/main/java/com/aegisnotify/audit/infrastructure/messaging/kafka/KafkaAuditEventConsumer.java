package com.aegisnotify.audit.infrastructure.messaging.kafka;

import com.aegisnotify.audit.application.dto.AuditEventCommand;
import com.aegisnotify.audit.application.port.in.ConsumeAuditEventUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for audit events published by the notification-service.
 *
 * <p>Consumes events from {@code notification-audit-events} topic with
 * manual acknowledgment. Delegates processing to
 * {@link ConsumeAuditEventUseCase} which handles encryption and
 * persistence. If processing fails, the offset is NOT acknowledged
 * so the message can be retried.</p>
 */
@Component
public class KafkaAuditEventConsumer {

  private static final Logger log =
      LoggerFactory.getLogger(KafkaAuditEventConsumer.class);

  private final ConsumeAuditEventUseCase consumeAuditEventUseCase;

  public KafkaAuditEventConsumer(
      ConsumeAuditEventUseCase consumeAuditEventUseCase) {
    this.consumeAuditEventUseCase = consumeAuditEventUseCase;
  }

  /**
   * Consumes an audit event and acknowledges the offset on success.
   *
   * @param record the Kafka consumer record containing the event
   * @param ack manual acknowledgment handle
   */
  @KafkaListener(
      topics = "${audit.topic:notification-audit-events}",
      groupId = "${audit.consumer.group-id:audit-service}",
      containerFactory = "auditKafkaListenerContainerFactory"
  )
  public void consume(
      ConsumerRecord<String, AuditEventCommand> record,
      Acknowledgment ack) {
    log.info("Received audit event for notification: {} with status: {}",
        record.key(), record.value().status());

    consumeAuditEventUseCase.consume(record.value());
    ack.acknowledge();

    log.debug("Audit event processed and acknowledged for offset: {}",
        record.offset());
  }
}
