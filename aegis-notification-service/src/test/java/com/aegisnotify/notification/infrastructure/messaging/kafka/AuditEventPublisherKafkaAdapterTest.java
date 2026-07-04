package com.aegisnotify.notification.infrastructure.messaging.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AuditEventPublisherKafkaAdapterTest {

  private static final String TOPIC = "notification-audit-events";

  @Mock
  private KafkaTemplate<String, AuditEventMessage> kafkaTemplate;

  private AuditEventPublisherKafkaAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new AuditEventPublisherKafkaAdapter(kafkaTemplate, TOPIC);
  }

  @Test
  void publish_noActiveTransaction_sendsImmediately() {
    UUID notificationId = UUID.randomUUID();
    AuditEventMessage message = new AuditEventMessage(
        notificationId, "PENDING", "Notification created",
        "EMAIL", "user@example.com", "HIGH", Instant.now()
    );

    CompletableFuture<SendResult<String, AuditEventMessage>> future =
        CompletableFuture.completedFuture(
            new SendResult<>(
                new ProducerRecord<>(TOPIC, notificationId.toString(), message),
                new RecordMetadata(new TopicPartition(TOPIC, 0), 0, 0, 0, 0, 0)
            )
        );
    when(kafkaTemplate.send(eq(TOPIC), eq(notificationId.toString()), eq(message)))
        .thenReturn(future);

    adapter.publish(message);

    verify(kafkaTemplate).send(TOPIC, notificationId.toString(), message);
  }

  @Test
  void publish_kafkaTemplateThrows_doesNotPropagate() {
    UUID notificationId = UUID.randomUUID();
    AuditEventMessage message = new AuditEventMessage(
        notificationId, "SENT", "Provider confirmation",
        "SMS", "+34600000000", "MEDIUM", Instant.now()
    );

    when(kafkaTemplate.send(eq(TOPIC), eq(notificationId.toString()), eq(message)))
        .thenThrow(new RuntimeException("Kafka unavailable"));

    // Must NOT throw — fire-and-forget
    adapter.publish(message);

    verify(kafkaTemplate).send(TOPIC, notificationId.toString(), message);
  }

  @Test
  void publish_withActiveTransaction_registersAfterCommitCallback() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      UUID notificationId = UUID.randomUUID();
      AuditEventMessage message = new AuditEventMessage(
          notificationId, "QUEUED", "Published to outbox",
          "EMAIL", "user@example.com", "LOW", Instant.now()
      );

      adapter.publish(message);

      // Should NOT have sent yet — registered for after commit
      verify(kafkaTemplate, never()).send(any(String.class), any(String.class),
          any(AuditEventMessage.class));

      // Simulate commit
      for (TransactionSynchronization sync
          : TransactionSynchronizationManager.getSynchronizations()) {
        sync.afterCommit();
      }

      verify(kafkaTemplate).send(TOPIC, notificationId.toString(), message);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void publish_withActiveTransactionAndRollback_doesNotSend() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      UUID notificationId = UUID.randomUUID();
      AuditEventMessage message = new AuditEventMessage(
          notificationId, "PROCESSING", "Processing started",
          "WHATSAPP", "+34600000001", "HIGH", Instant.now()
      );

      adapter.publish(message);

      // Simulate rollback by NOT calling afterCommit — just clear
      // The send should never happen
      verify(kafkaTemplate, never()).send(any(String.class), any(String.class),
          any(AuditEventMessage.class));
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }
}
