package com.aegisnotify.notification.infrastructure.config;

import com.aegisnotify.notification.application.port.out.MessageBrokerPort;
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
