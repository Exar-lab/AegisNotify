package com.aegisnotify.notification.infrastructure.messaging.kafka;

import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Kafka-based implementation of {@link DeadLetterQueuePort} (issue #30).
 *
 * <p>Publishes to the dedicated {@code notification.kafka.topics.dlq} topic
 * (default {@code notifications-dlq}), keyed by the notification id so all
 * DLQ entries for a given notification land on the same partition. Both the
 * notification id and the failure reason are attached as real Kafka
 * {@link org.apache.kafka.common.header.Headers} — not only embedded in the
 * JSON payload body — so a DLQ consumer or tooling can filter/route on them
 * without deserializing the body (acceptance criterion of #30).</p>
 *
 * <p><strong>Failure-propagation decision:</strong> this adapter is called
 * from two places, neither of which wraps the call in its own try/catch:</p>
 *
 * <ul>
 *   <li>{@code ConsumeNotificationEventService.consume(UUID)} — a
 *   {@code @KafkaListener} method with manual acknowledgment. Both of its
 *   call sites for {@code sendToDlq} are the last resort after a notification
 *   has already exhausted its primary and fallback providers (or thrown an
 *   unexpected exception). There is no further application-level fallback
 *   past the DLQ publish itself.</li>
 *   <li>{@code SendToDlqService.sendToDlq(UUID, String)} — a
 *   {@code @Transactional} manual-intervention use case.</li>
 * </ul>
 *
 * <p>Despite DLQ being the last resort, this adapter still throws
 * {@link DeadLetterQueuePublishException} on failure instead of swallowing it,
 * for two reasons:</p>
 *
 * <ol>
 *   <li>For {@code SendToDlqService}, swallowing would let the surrounding
 *   {@code @Transactional} method commit a notification row marked
 *   {@code FAILED_CRITICAL} that was never actually mirrored to the DLQ —
 *   an operator believing "sent to DLQ" succeeded when it silently did not.
 *   Propagating rolls that transaction back so the caller (and any retry UI)
 *   sees the failure honestly.</li>
 *   <li>For {@code ConsumeNotificationEventService.consume}, propagating
 *   means the exception escapes the listener method without calling
 *   {@code acknowledgment.acknowledge()}. That is not a dead end: it hands
 *   the record to {@code KafkaConsumerConfig}'s existing
 *   {@code DefaultErrorHandler}, which retries with backoff and then
 *   publishes the original record to its per-source-topic
 *   {@code notification.kafka.topics.dlt-suffix} topic. In other words, a
 *   failure to reach the structured {@code notifications-dlq} topic (with
 *   its headers) still does not lose the record — it falls through to the
 *   coarser, already-wired topic-level DLT safety net instead of vanishing.
 *   Swallowing here would discard that existing safety net for no benefit.</li>
 * </ol>
 */
public class KafkaDeadLetterQueueAdapter implements DeadLetterQueuePort {

  private static final Logger log = LoggerFactory.getLogger(KafkaDeadLetterQueueAdapter.class);

  private static final Duration ACK_TIMEOUT = Duration.ofSeconds(10);
  private static final String METER_PUBLISH_ERRORS = "aegisnotify.dlq.publish.error.count";
  private static final String HEADER_NOTIFICATION_ID = "notificationId";
  private static final String HEADER_REASON = "reason";

  private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;
  private final String dlqTopic;
  private final MeterRegistry meterRegistry;

  public KafkaDeadLetterQueueAdapter(
      KafkaTemplate<String, Map<String, Object>> kafkaTemplate,
      String dlqTopic,
      MeterRegistry meterRegistry) {
    this.kafkaTemplate = kafkaTemplate;
    this.dlqTopic = dlqTopic;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void sendToDlq(UUID notificationId, Map<String, Object> payload, String reason) {
    String key = notificationId == null ? null : notificationId.toString();

    ProducerRecord<String, Map<String, Object>> record =
        new ProducerRecord<>(dlqTopic, key, payload);
    record.headers().add(HEADER_NOTIFICATION_ID, headerBytes(key));
    record.headers().add(HEADER_REASON, headerBytes(reason));

    CompletableFuture<SendResult<String, Map<String, Object>>> future =
        kafkaTemplate.send(record);

    try {
      future.get(ACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.warn("dlq_publish_interrupted topic={} notificationId={}", dlqTopic, notificationId, ex);
      meterRegistry.counter(METER_PUBLISH_ERRORS, "reason", "interrupted").increment();
      throw new DeadLetterQueuePublishException(
          "Interrupted while publishing to DLQ topic " + dlqTopic, ex);
    } catch (ExecutionException ex) {
      Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
      log.warn("dlq_publish_failed topic={} notificationId={} reason={}",
          dlqTopic, notificationId, cause.getMessage(), cause);
      meterRegistry.counter(METER_PUBLISH_ERRORS, "reason", "execution").increment();
      throw new DeadLetterQueuePublishException(
          "Failed to publish to DLQ topic " + dlqTopic, cause);
    } catch (TimeoutException ex) {
      // Same inherent limitation as KafkaMessageBrokerAdapter: cancel() cannot
      // abort an already-dispatched send, only our view of its future. Still
      // attempted so an abandoned-but-later-successful send is a visible,
      // traceable risk instead of a silent one.
      boolean cancelled = future.cancel(true);
      log.warn("dlq_publish_timed_out topic={} notificationId={} cancelledFuture={}",
          dlqTopic, notificationId, cancelled, ex);
      meterRegistry.counter(METER_PUBLISH_ERRORS, "reason", "timeout").increment();
      throw new DeadLetterQueuePublishException(
          "Timed out waiting for broker acknowledgement for DLQ topic " + dlqTopic, ex);
    }
  }

  private static byte[] headerBytes(String value) {
    return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
  }
}
