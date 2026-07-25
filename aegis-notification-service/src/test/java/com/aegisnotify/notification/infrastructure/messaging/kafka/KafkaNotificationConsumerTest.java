package com.aegisnotify.notification.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aegisnotify.notification.application.dto.NotificationEvent;
import com.aegisnotify.notification.application.port.in.ConsumeNotificationEventUseCase;
import com.aegisnotify.notification.infrastructure.config.NotificationKafkaMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class KafkaNotificationConsumerTest {

  @Mock
  private ConsumeNotificationEventUseCase consumeNotificationEventUseCase;

  @Mock
  private Acknowledgment acknowledgment;

  private SimpleMeterRegistry meterRegistry;
  private KafkaNotificationConsumer consumer;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    consumer = new KafkaNotificationConsumer(
        consumeNotificationEventUseCase, new NotificationKafkaMetrics(meterRegistry));
  }

  @Test
  void consume_delegatesAndAcknowledgesAfterSuccess() {
    UUID notificationId = UUID.randomUUID();
    NotificationEvent event = new NotificationEvent(
        notificationId, "EMAIL", "user@example.com", "welcome",
        Map.of("name", "John"), "HIGH"
    );
    ConsumerRecord<String, NotificationEvent> record = new ConsumerRecord<>(
        "high-priority-topic", 0, 0L, notificationId.toString(), event);

    consumer.consume(record, acknowledgment);

    verify(consumeNotificationEventUseCase).consume(notificationId);
    verify(acknowledgment).acknowledge();
    assertThat(meterRegistry.find("notification.kafka.consumer.success")
        .tags("topic", "high-priority-topic")
        .counter().count()).isEqualTo(1.0d);
  }

  @Test
  void consume_whenUseCaseFailsDoesNotAcknowledge() {
    UUID notificationId = UUID.randomUUID();
    NotificationEvent event = new NotificationEvent(
        notificationId, "EMAIL", "user@example.com", "welcome",
        Map.of(), "HIGH"
    );
    ConsumerRecord<String, NotificationEvent> record = new ConsumerRecord<>(
        "high-priority-topic", 0, 1L, notificationId.toString(), event);

    doThrow(new RuntimeException("processing failed"))
        .when(consumeNotificationEventUseCase).consume(notificationId);

    assertThrows(RuntimeException.class,
        () -> consumer.consume(record, acknowledgment));

    verify(consumeNotificationEventUseCase).consume(notificationId);
    verifyNoInteractions(acknowledgment);
  }
}
