package com.aegisnotify.notification.infrastructure.config;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.infrastructure.messaging.kafka.AuditEventPublisherKafkaAdapter;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Kafka producer configuration for audit event publishing.
 *
 * <p>Configures a {@link KafkaTemplate} with {@code acks=all} and idempotence
 * enabled for durable, exactly-once producer semantics. Uses
 * {@link JsonSerializer} for the {@link AuditEventMessage} value.</p>
 *
 * <p>{@code reconnect.backoff(.max).ms} are widened from the client's
 * defaults (50ms / 1000ms) to 1s / 30s — same fix and same reasoning as
 * {@link KafkaMessageBrokerConfig}'s {@code messageBrokerProducerFactory}:
 * retries stay unbounded (idempotence implies that already), only the
 * reconnect-attempt frequency against an unreachable broker changes, from
 * roughly once a second to a capped exponential backoff.</p>
 */
@Configuration
@ConditionalOnProperty(name = "audit.publishing.enabled", matchIfMissing = true)
public class KafkaProducerConfig {

  @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
  private String bootstrapServers;

  @Bean
  public ProducerFactory<String, AuditEventMessage> auditProducerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    props.put(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, 1_000);
    props.put(ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, 30_000);
    props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, AuditEventMessage> auditKafkaTemplate(
      ProducerFactory<String, AuditEventMessage> auditProducerFactory) {
    return new KafkaTemplate<>(auditProducerFactory);
  }

  @Bean
  public AuditEventPublisherKafkaAdapter auditEventPublisherKafkaAdapter(
      KafkaTemplate<String, AuditEventMessage> auditKafkaTemplate,
      @Value("${audit.topic:notification-audit-events}") String topic) {
    return new AuditEventPublisherKafkaAdapter(auditKafkaTemplate, topic);
  }
}
