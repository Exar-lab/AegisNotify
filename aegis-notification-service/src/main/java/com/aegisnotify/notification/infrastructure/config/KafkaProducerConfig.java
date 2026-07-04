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
