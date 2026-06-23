package com.aegisnotify.audit.infrastructure.messaging.kafka;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aegisnotify.audit.application.dto.AuditEventCommand;
import com.aegisnotify.audit.application.port.in.ConsumeAuditEventUseCase;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class KafkaAuditEventConsumerTest {

  @Mock
  private ConsumeAuditEventUseCase consumeAuditEventUseCase;

  @Mock
  private Acknowledgment acknowledgment;

  @InjectMocks
  private KafkaAuditEventConsumer consumer;

  @Test
  void consume_validEvent_delegatesToUseCaseAndAcknowledges() {
    UUID notificationId = UUID.randomUUID();
    AuditEventCommand command = new AuditEventCommand(
        notificationId, "SENT", "Delivered via SendGrid",
        "EMAIL", "user@example.com", "HIGH", Instant.now()
    );
    ConsumerRecord<String, AuditEventCommand> record =
        new ConsumerRecord<>(
            "notification-audit-events", 0, 0L,
            notificationId.toString(), command
        );

    consumer.consume(record, acknowledgment);

    verify(consumeAuditEventUseCase).consume(argThat(cmd ->
        cmd.notificationId().equals(notificationId)
            && cmd.status().equals("SENT")
            && cmd.channel().equals("EMAIL")
    ));
    verify(acknowledgment).acknowledge();
  }

  @Test
  void consume_smsEvent_parsesCorrectly() {
    UUID notificationId = UUID.randomUUID();
    AuditEventCommand command = new AuditEventCommand(
        notificationId, "QUEUED", "Published to outbox",
        "SMS", "+34600000000", "MEDIUM", Instant.now()
    );
    ConsumerRecord<String, AuditEventCommand> record =
        new ConsumerRecord<>(
            "notification-audit-events", 0, 1L,
            notificationId.toString(), command
        );

    consumer.consume(record, acknowledgment);

    verify(consumeAuditEventUseCase).consume(argThat(cmd ->
        cmd.status().equals("QUEUED")
            && cmd.channel().equals("SMS")
            && cmd.recipient().equals("+34600000000")
    ));
    verify(acknowledgment).acknowledge();
  }

  @Test
  void consume_useCaseThrows_doesNotAcknowledge() {
    UUID notificationId = UUID.randomUUID();
    AuditEventCommand command = new AuditEventCommand(
        notificationId, "SENT", "Delivered",
        "EMAIL", "user@example.com", "HIGH", Instant.now()
    );
    ConsumerRecord<String, AuditEventCommand> record =
        new ConsumerRecord<>(
            "notification-audit-events", 0, 2L,
            notificationId.toString(), command
        );
    doThrow(new RuntimeException("MongoDB unavailable"))
        .when(consumeAuditEventUseCase).consume(command);

    try {
      consumer.consume(record, acknowledgment);
    } catch (RuntimeException ignored) {
      // Expected — error handler will catch this
    }

    verifyNoInteractions(acknowledgment);
  }
}
