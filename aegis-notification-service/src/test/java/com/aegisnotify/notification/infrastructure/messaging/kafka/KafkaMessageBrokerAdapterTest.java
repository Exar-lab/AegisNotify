package com.aegisnotify.notification.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Unit tests for {@link KafkaMessageBrokerAdapter} (issue #27, K2/K3 of the design).
 *
 * <p>Covers: logical-to-configured topic alias resolution (K2), unknown-topic
 * passthrough, and send-failure propagation (K3 — the deliberate deviation
 * from {@link AuditEventPublisherKafkaAdapter}'s fire-and-forget swallow).</p>
 */
@ExtendWith(MockitoExtension.class)
class KafkaMessageBrokerAdapterTest {

  @Mock
  private KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

  private SimpleMeterRegistry meterRegistry;
  private KafkaMessageBrokerAdapter adapter;

  @BeforeEach
  void setUp() {
    Map<String, String> topicAliases = Map.of(
        "high-priority-topic", "configured-high-priority-topic",
        "medium-priority-topic", "configured-medium-priority-topic",
        "low-priority-topic", "configured-low-priority-topic"
    );
    meterRegistry = new SimpleMeterRegistry();
    adapter = new KafkaMessageBrokerAdapter(kafkaTemplate, topicAliases, meterRegistry);
  }

  @AfterEach
  void clearInterruptFlag() {
    // Defensive: an interrupted-flow test restores the flag itself, but clear it
    // here too so a failed assertion never leaks an interrupted thread into other tests.
    Thread.interrupted();
  }

  @Test
  void publish_aliasedTopic_resolvesToConfiguredTopicAndSendsPayloadWithIdKey() {
    UUID notificationId = UUID.randomUUID();
    Map<String, Object> payload = Map.of("id", notificationId.toString(), "priority", "HIGH");

    when(kafkaTemplate.send(eq("configured-high-priority-topic"),
        eq(notificationId.toString()), eq(payload)))
        .thenReturn(completedFuture("configured-high-priority-topic",
            notificationId.toString(), payload));

    adapter.publish("high-priority-topic", payload);

    verify(kafkaTemplate).send("configured-high-priority-topic",
        notificationId.toString(), payload);
  }

  @Test
  void publish_secondAliasedTopic_resolvesToItsOwnConfiguredTopic() {
    UUID notificationId = UUID.randomUUID();
    Map<String, Object> payload = Map.of("id", notificationId.toString(), "priority", "LOW");

    when(kafkaTemplate.send(eq("configured-low-priority-topic"),
        eq(notificationId.toString()), eq(payload)))
        .thenReturn(completedFuture("configured-low-priority-topic",
            notificationId.toString(), payload));

    adapter.publish("low-priority-topic", payload);

    verify(kafkaTemplate).send("configured-low-priority-topic",
        notificationId.toString(), payload);
  }

  @Test
  void publish_unknownTopic_passesThroughUnchanged() {
    UUID notificationId = UUID.randomUUID();
    Map<String, Object> payload = Map.of("id", notificationId.toString(), "priority", "MEDIUM");

    when(kafkaTemplate.send(eq("some-unmapped-topic"), eq(notificationId.toString()), eq(payload)))
        .thenReturn(completedFuture("some-unmapped-topic", notificationId.toString(), payload));

    adapter.publish("some-unmapped-topic", payload);

    verify(kafkaTemplate).send("some-unmapped-topic", notificationId.toString(), payload);
  }

  @Test
  void publish_sendFailure_propagatesException() {
    UUID notificationId = UUID.randomUUID();
    Map<String, Object> payload = Map.of("id", notificationId.toString(), "priority", "MEDIUM");

    CompletableFuture<SendResult<String, Map<String, Object>>> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("Kafka unavailable"));
    when(kafkaTemplate.send(eq("configured-medium-priority-topic"),
        eq(notificationId.toString()), eq(payload)))
        .thenReturn(failed);

    // Contrast with AuditEventPublisherKafkaAdapter, which swallows send failures —
    // this adapter MUST propagate so the caller's @Transactional method rolls back (K3).
    assertThatThrownBy(() -> adapter.publish("medium-priority-topic", payload))
        .isInstanceOf(MessageBrokerPublishException.class)
        .hasCauseInstanceOf(RuntimeException.class);

    assertThat(meterRegistry.find("aegisnotify.outbox.publish.error.count")
        .tag("reason", "execution").counter()).isNotNull();
  }

  @Test
  void publish_timeout_throwsAndAttemptsToCancelTheAbandonedSend() {
    UUID notificationId = UUID.randomUUID();
    Map<String, Object> payload = Map.of("id", notificationId.toString(), "priority", "HIGH");

    // A future that never completes and deterministically reports a timeout on
    // get(timeout, unit) — no real sleep/timing race involved.
    CompletableFuture<SendResult<String, Map<String, Object>>> timingOut =
        new CompletableFuture<>() {
          @Override
          public SendResult<String, Map<String, Object>> get(long timeout, TimeUnit unit)
              throws TimeoutException {
            throw new TimeoutException("simulated broker ack timeout");
          }
        };
    when(kafkaTemplate.send(eq("configured-high-priority-topic"),
        eq(notificationId.toString()), eq(payload)))
        .thenReturn(timingOut);

    assertThatThrownBy(() -> adapter.publish("high-priority-topic", payload))
        .isInstanceOf(MessageBrokerPublishException.class)
        .hasCauseInstanceOf(TimeoutException.class);

    // The adapter must at least attempt to cancel the abandoned send so it does not
    // silently keep running in the background with no trace of the risk.
    assertThat(timingOut.isCancelled()).isTrue();
    assertThat(meterRegistry.find("aegisnotify.outbox.publish.error.count")
        .tag("reason", "timeout").counter()).isNotNull();
  }

  @Test
  void publish_interrupted_throwsAndRestoresInterruptFlag() {
    UUID notificationId = UUID.randomUUID();
    Map<String, Object> payload = Map.of("id", notificationId.toString(), "priority", "HIGH");

    CompletableFuture<SendResult<String, Map<String, Object>>> interrupting =
        new CompletableFuture<>() {
          @Override
          public SendResult<String, Map<String, Object>> get(long timeout, TimeUnit unit)
              throws InterruptedException {
            throw new InterruptedException("simulated interrupt");
          }
        };
    when(kafkaTemplate.send(eq("configured-high-priority-topic"),
        eq(notificationId.toString()), eq(payload)))
        .thenReturn(interrupting);

    try {
      assertThatThrownBy(() -> adapter.publish("high-priority-topic", payload))
          .isInstanceOf(MessageBrokerPublishException.class)
          .hasCauseInstanceOf(InterruptedException.class);

      // Swallowing an interrupt without restoring the flag is a known anti-pattern;
      // the adapter must leave the thread's interrupt status set for callers to observe.
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(meterRegistry.find("aegisnotify.outbox.publish.error.count")
          .tag("reason", "interrupted").counter()).isNotNull();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void publish_missingIdInPayload_sendsWithNullKey() {
    Map<String, Object> payload = Map.of("priority", "LOW");

    when(kafkaTemplate.send(eq("configured-low-priority-topic"), eq((String) null), eq(payload)))
        .thenReturn(completedFuture("configured-low-priority-topic", null, payload));

    adapter.publish("low-priority-topic", payload);

    verify(kafkaTemplate).send("configured-low-priority-topic", (String) null, payload);
  }

  private static CompletableFuture<SendResult<String, Map<String, Object>>> completedFuture(
      String topic, String key, Map<String, Object> payload) {
    return CompletableFuture.completedFuture(new SendResult<>(
        new ProducerRecord<>(topic, key, payload),
        new RecordMetadata(new TopicPartition(topic, 0), 0, 0, 0, 0, 0)));
  }
}
