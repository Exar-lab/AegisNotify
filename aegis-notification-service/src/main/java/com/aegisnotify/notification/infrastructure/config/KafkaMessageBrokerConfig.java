package com.aegisnotify.notification.infrastructure.config;

import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.application.port.out.MessageBrokerPort;
import com.aegisnotify.notification.infrastructure.messaging.kafka.KafkaDeadLetterQueueAdapter;
import com.aegisnotify.notification.infrastructure.messaging.kafka.KafkaMessageBrokerAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Kafka producer configuration for the outbox delivery relay (issue #27, K4).
 *
 * <p>Provides a dedicated {@link ProducerFactory}/{@link KafkaTemplate}
 * for {@link KafkaMessageBrokerAdapter}, mirroring {@link KafkaProducerConfig}'s
 * durability settings ({@code acks=all}, {@code enable.idempotence=true}) but
 * deliberately NOT reusing {@code auditKafkaTemplate}: that bean is typed to
 * {@code AuditEventMessage} and gated on {@code audit.publishing.enabled} —
 * the delivery-critical relay must not depend on audit configuration.</p>
 *
 * <p><strong>DLQ producer reuse (issue #30):</strong> {@link KafkaDeadLetterQueueAdapter}
 * reuses this same {@code messageBrokerKafkaTemplate}/{@code messageBrokerProducerFactory}
 * pair instead of a dedicated one: {@link DeadLetterQueuePort#sendToDlq} already
 * takes a {@code Map<String, Object>} payload — the exact same wire type this
 * producer is configured for — and this producer already carries the
 * {@code acks=all} + idempotence durability settings #30 asks for. A second,
 * separately-pooled producer connected to the same broker for the same value
 * type would duplicate connections and configuration drift risk for no
 * isolation benefit (unlike the audit template, which is a genuinely
 * different value type gated on a different, non-delivery-critical flag).</p>
 *
 * <p>{@code retries} is set explicitly (#30's literal acceptance criterion)
 * even though {@code enable.idempotence=true} already implies an effectively
 * unbounded retry count by default in this Kafka client version — making it
 * explicit documents the intent instead of relying on an implicit default
 * that could change with a future client upgrade.</p>
 *
 * <p>{@code reconnect.backoff(.max).ms} are widened from the client's
 * defaults (50ms / 1000ms) to 1s / 30s. The retry count itself is still
 * unbounded — a broker outage never drops a notification — only how often
 * each retry is attempted changes. At the default backoff, an unreachable
 * broker makes {@code NetworkClient} log a reconnect warning roughly once a
 * second for as long as it stays down, which is noise, not signal, once
 * you already know Kafka is unavailable; the capped exponential backoff
 * still reaches a broker that comes back up within 30s of its next
 * attempt.</p>
 */
@Configuration
public class KafkaMessageBrokerConfig {

  private final String bootstrapServers;

  public KafkaMessageBrokerConfig(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  @Bean
  public ProducerFactory<String, Map<String, Object>> messageBrokerProducerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
    props.put(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, 1_000);
    props.put(ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, 30_000);
    props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, Map<String, Object>> messageBrokerKafkaTemplate(
      ProducerFactory<String, Map<String, Object>> messageBrokerProducerFactory) {
    return new KafkaTemplate<>(messageBrokerProducerFactory);
  }

  @Bean
  public MessageBrokerPort messageBrokerPort(
      KafkaTemplate<String, Map<String, Object>> messageBrokerKafkaTemplate,
      NotificationKafkaProperties notificationKafkaProperties,
      MeterRegistry meterRegistry) {
    return new KafkaMessageBrokerAdapter(
        messageBrokerKafkaTemplate, topicAliases(notificationKafkaProperties), meterRegistry);
  }

  @Bean
  public DeadLetterQueuePort deadLetterQueuePort(
      KafkaTemplate<String, Map<String, Object>> messageBrokerKafkaTemplate,
      NotificationKafkaProperties notificationKafkaProperties,
      MeterRegistry meterRegistry) {
    return new KafkaDeadLetterQueueAdapter(
        messageBrokerKafkaTemplate, notificationKafkaProperties.topics().dlq(), meterRegistry);
  }

  /**
   * Builds the logical-to-configured topic alias map (K2): the literal names
   * hardcoded in {@code PublishOutboxEventTransactions.TOPIC_MAP} resolve to
   * the actually-configured {@code notification.kafka.topics.*} names, so an
   * env-var topic override cannot silently publish to a topic nobody
   * consumes. Pure function — no Spring required to test it.
   */
  static Map<String, String> topicAliases(NotificationKafkaProperties properties) {
    Map<String, String> aliases = new HashMap<>();
    aliases.put("high-priority-topic", properties.topics().highPriority());
    aliases.put("medium-priority-topic", properties.topics().mediumPriority());
    aliases.put("low-priority-topic", properties.topics().lowPriority());
    return aliases;
  }
}
