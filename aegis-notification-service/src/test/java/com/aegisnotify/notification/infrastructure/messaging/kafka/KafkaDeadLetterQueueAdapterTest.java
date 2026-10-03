package com.aegisnotify.notification.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Unit tests for {@link KafkaDeadLetterQueueAdapter} (issue #30).
 *
 * <p>Covers: publishing to the configured DLQ topic with the notification id
 * as the partition key, attaching notificationId/reason as real Kafka
 * headers (not just the payload body), and failure propagation (mirroring
 * {@link KafkaMessageBrokerAdapterTest}'s K3-style coverage).</p>
 */
@ExtendWith(MockitoExtension.class)
class KafkaDeadLetterQueueAdapterTest {

  private static final String DLQ_TOPIC = "notifications-dlq";

  @Mock
  private KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

  private SimpleMeterRegistry meterRegistry;
  private KafkaDeadLetterQueueAdapter adapter;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    adapter = new KafkaDeadLetterQueueAdapter(kafkaTemplate, DLQ_TOPIC, meterRegistry);
  }

  @AfterEach
  void clearInterruptFlag() {
    Thread.interrupted();
  }

  @Test
  void sendToDlq_publishesToConfiguredTopicWithNotificationIdKeyAndHeaders() {
    UUID notificationId = UUID.randomUUID();
    Map<String, Object> payload = Map.of("notificationId", notificationId.toString());
    String reason = "Critical failure after processing";

    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(completedFuture(DLQ_TOPIC, notificationId.toString(), payload));

    adapter.sendToDlq(notificationId, payload, reason);

    ArgumentCaptor<ProducerRecord<String, Map<String, Object>>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(captor.capture());

    ProducerRecord<String, Map<String, Object>> sent = captor.getValue();
    assertThat(sent.topic()).isEqualTo(DLQ_TOPIC);
    assertThat(sent.key()).isEqualTo(notificationId.toString());
    assertThat(sent.value()).isEqualTo(payload);

    assertThat(sent.headers().lastHeader("notificationId").value())
        .isEqualTo(notificationId.toString().getBytes(StandardCharsets.UTF_8));
    assertThat(sent.headers().lastHeader("reason").value())
        .isEqualTo(reason.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void sendToDlq_nullReason_sendsEmptyReasonHeaderWithoutThrowing() {
    UUID notificationId = UUID.randomUUID();
    Map<String, Object> payload = Map.of("notificationId", notificationId.toString());

    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(completedFuture(DLQ_TOPIC, notificationId.toString(), payload));

    adapter.sendToDlq(notificationId, payload, null);

    ArgumentCaptor<ProducerRecord<String, Map<String, Object>>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(captor.capture());

    assertThat(captor.getValue().headers().lastHeader("reason").value()).isEmpty();
  }

  @Test
  void sendToDlq_sendFailure_propagatesException() {
    UUID notificationId = UUID.randomUUID();
    Map<String, Object> payload = Map.of("notificationId", notificationId.toString());

    CompletableFuture<SendResult<String, Map<String, Object>>> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("Kafka unavailable"));
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

    // Mirrors KafkaMessageBrokerAdapter's K3: DLQ publish failure must NOT be
    // swallowed (see class Javadoc for why — the topic-level DLT fallback
    // only kicks in if this exception actually escapes the caller).
    assertThatThrownBy(() -> adapter.sendToDlq(notificationId, payload, "some reason"))
        .isInstanceOf(DeadLetterQueuePublishException.class)
        .hasCauseInstanceOf(RuntimeException.class);

    assertThat(meterRegistry.find("aegisnotify.dlq.publish.error.count")
        .tag("reason", "execution").counter()).isNotNull();
  }

  @Test
  void sendToDlq_timeout_throwsAndAttemptsToCancelTheAbandonedSend() {
    UUID notificationId = UUID.randomUUID();
    Map<String, Object> payload = Map.of("notificationId", notificationId.toString());

    CompletableFuture<SendResult<String, Map<String, Object>>> timingOut =
        new CompletableFuture<>() {
          @Override
          public SendResult<String, Map<String, Object>> get(long timeout, TimeUnit unit)
              throws TimeoutException {
            throw new TimeoutException("simulated broker ack timeout");
          }
        };
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(timingOut);

    assertThatThrownBy(() -> adapter.sendToDlq(notificationId, payload, "some reason"))
        .isInstanceOf(DeadLetterQueuePublishException.class)
        .hasCauseInstanceOf(TimeoutException.class);

    assertThat(timingOut.isCancelled()).isTrue();
    assertThat(meterRegistry.find("aegisnotify.dlq.publish.error.count")
        .tag("reason", "timeout").counter()).isNotNull();
  }

  @Test
  void sendToDlq_interrupted_throwsAndRestoresInterruptFlag() {
    UUID notificationId = UUID.randomUUID();
    Map<String, Object> payload = Map.of("notificationId", notificationId.toString());

    CompletableFuture<SendResult<String, Map<String, Object>>> interrupting =
        new CompletableFuture<>() {
          @Override
          public SendResult<String, Map<String, Object>> get(long timeout, TimeUnit unit)
              throws InterruptedException {
            throw new InterruptedException("simulated interrupt");
          }
        };
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(interrupting);

    try {
      assertThatThrownBy(() -> adapter.sendToDlq(notificationId, payload, "some reason"))
          .isInstanceOf(DeadLetterQueuePublishException.class)
          .hasCauseInstanceOf(InterruptedException.class);

      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(meterRegistry.find("aegisnotify.dlq.publish.error.count")
          .tag("reason", "interrupted").counter()).isNotNull();
    } finally {
      Thread.interrupted();
    }
  }

  private static CompletableFuture<SendResult<String, Map<String, Object>>> completedFuture(
      String topic, String key, Map<String, Object> payload) {
    return CompletableFuture.completedFuture(new SendResult<>(
        new ProducerRecord<>(topic, key, payload),
        new RecordMetadata(new TopicPartition(topic, 0), 0, 0, 0, 0, 0)));
  }
}
