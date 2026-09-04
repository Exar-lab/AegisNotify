package com.aegisnotify.notification.infrastructure.messaging.kafka;

import com.aegisnotify.notification.application.port.out.MessageBrokerPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Kafka-based implementation of {@link MessageBrokerPort} (issue #27).
 *
 * <p>Resolves the logical topic name it is handed to the physically
 * configured topic name via an alias map built from
 * {@link com.aegisnotify.notification.infrastructure.config.NotificationKafkaProperties}
 * (K2); an unresolved name passes through unchanged. The message key is the
 * notification id read from {@code payload.get("id")}, matching the ordering
 * guarantee used by {@link AuditEventPublisherKafkaAdapter}.</p>
 *
 * <p><strong>K3 — deliberate deviation from #27's literal "deferred
 * after-commit" acceptance criterion:</strong> unlike
 * {@link AuditEventPublisherKafkaAdapter}, which defers the send to after
 * commit and swallows failures, this adapter sends synchronously, inside
 * whatever transaction the caller is already in, with a bounded wait for the
 * broker acknowledgement. A failure is wrapped in
 * {@link MessageBrokerPublishException} and propagated — never swallowed —
 * so the enclosing {@code @Transactional} method (which marks the outbox row
 * {@code PROCESSED} in the same transaction) rolls back to
 * {@code UNPROCESSED} for retry on the next relay poll, instead of silently
 * losing a notification that was already marked delivered.</p>
 */
public class KafkaMessageBrokerAdapter implements MessageBrokerPort {

  private static final Logger log = LoggerFactory.getLogger(KafkaMessageBrokerAdapter.class);

  private static final Duration ACK_TIMEOUT = Duration.ofSeconds(10);
  private static final String METER_PUBLISH_ERRORS = "aegisnotify.outbox.publish.error.count";

  private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;
  private final Map<String, String> topicAliases;
  private final MeterRegistry meterRegistry;

  public KafkaMessageBrokerAdapter(
      KafkaTemplate<String, Map<String, Object>> kafkaTemplate,
      Map<String, String> topicAliases,
      MeterRegistry meterRegistry) {
    this.kafkaTemplate = kafkaTemplate;
    this.topicAliases = Map.copyOf(topicAliases);
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void publish(String topic, Map<String, Object> payload) {
    String resolvedTopic = topicAliases.getOrDefault(topic, topic);
    String key = extractKey(payload);

    CompletableFuture<SendResult<String, Map<String, Object>>> future =
        kafkaTemplate.send(resolvedTopic, key, payload);

    try {
      future.get(ACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.warn("kafka_publish_interrupted topic={}", resolvedTopic, ex);
      meterRegistry.counter(METER_PUBLISH_ERRORS, "reason", "interrupted").increment();
      throw new MessageBrokerPublishException(
          "Interrupted while publishing to topic " + resolvedTopic, ex);
    } catch (ExecutionException ex) {
      Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
      log.warn("kafka_publish_failed topic={} reason={}", resolvedTopic, cause.getMessage(), cause);
      meterRegistry.counter(METER_PUBLISH_ERRORS, "reason", "execution").increment();
      throw new MessageBrokerPublishException(
          "Failed to publish to topic " + resolvedTopic, cause);
    } catch (TimeoutException ex) {
      // The Kafka producer client offers no genuine mid-flight cancellation of an
      // already-dispatched send: cancel() only marks OUR CompletableFuture view as
      // cancelled, it cannot abort the in-flight broker request. We still attempt it
      // (in case the send has not yet been handed to the network client) and log the
      // outcome so an abandoned-send-that-later-succeeds is a visible, traceable risk
      // instead of a silent one.
      boolean cancelled = future.cancel(true);
      log.warn("kafka_publish_timed_out topic={} cancelledFuture={}", resolvedTopic, cancelled, ex);
      meterRegistry.counter(METER_PUBLISH_ERRORS, "reason", "timeout").increment();
      throw new MessageBrokerPublishException(
          "Timed out waiting for broker acknowledgement for topic " + resolvedTopic, ex);
    }
  }

  private static String extractKey(Map<String, Object> payload) {
    Object id = payload.get("id");
    return id == null ? null : id.toString();
  }
}
