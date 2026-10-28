package com.aegisnotify.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.infrastructure.messaging.kafka.KafkaDeadLetterQueueAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Unit tests for {@link KafkaMessageBrokerConfig} (issue #27, K4 of the design).
 *
 * <p>Asserts the dedicated producer factory carries durability-critical
 * settings ({@code acks=all}, idempotence) and wire-compatibility settings
 * (no type-info headers, matching {@link KafkaConsumerConfig}'s
 * {@code USE_TYPE_INFO_HEADERS=false}) — and that it is its own bean,
 * independent from {@code auditKafkaTemplate}.</p>
 */
class KafkaMessageBrokerConfigTest {

  private final KafkaMessageBrokerConfig config = new KafkaMessageBrokerConfig("localhost:9092");

  @Test
  void messageBrokerProducerFactory_configuresDeliveryCriticalDurabilitySettings() {
    ProducerFactory<String, Map<String, Object>> producerFactory =
        config.messageBrokerProducerFactory();

    Map<String, Object> props = producerFactory.getConfigurationProperties();

    assertThat(props.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
    assertThat(props.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isEqualTo(true);
    assertThat(props.get(ProducerConfig.RETRIES_CONFIG)).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void messageBrokerProducerFactory_widensReconnectBackoffWithoutBoundingRetries() {
    // The outbox relay must keep retrying an unreachable broker forever
    // (never drop a notification — asserted above), but at the client's
    // default 50ms/1000ms backoff that means a reconnect warning roughly
    // once a second for as long as the broker stays down. Widening the
    // backoff window only changes how often each retry fires, not whether
    // it keeps retrying.
    ProducerFactory<String, Map<String, Object>> producerFactory =
        config.messageBrokerProducerFactory();

    Map<String, Object> props = producerFactory.getConfigurationProperties();

    assertThat(props.get(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG)).isEqualTo(1_000);
    assertThat(props.get(ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG)).isEqualTo(30_000);
  }

  @Test
  void messageBrokerProducerFactory_configuresWireCompatibleSerializers() {
    ProducerFactory<String, Map<String, Object>> producerFactory =
        config.messageBrokerProducerFactory();

    Map<String, Object> props = producerFactory.getConfigurationProperties();

    assertThat(props.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)).isEqualTo(
        StringSerializer.class);
    assertThat(props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(
        JsonSerializer.class);
    assertThat(props.get(JsonSerializer.ADD_TYPE_INFO_HEADERS)).isEqualTo(false);
  }

  @Test
  void messageBrokerProducerFactory_usesConfiguredBootstrapServers() {
    ProducerFactory<String, Map<String, Object>> producerFactory =
        config.messageBrokerProducerFactory();

    Map<String, Object> props = producerFactory.getConfigurationProperties();

    assertThat(props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("localhost:9092");
  }

  @Test
  void topicAliases_mapsLogicalNamesToConfiguredTopics() {
    NotificationKafkaProperties properties = new NotificationKafkaProperties(
        new NotificationKafkaProperties.Relay(),
        new NotificationKafkaProperties.Consumer("notification-service"),
        new NotificationKafkaProperties.Topics(
            "custom-high-topic", "custom-medium-topic", "custom-low-topic",
            3, (short) 3, "-dlt"));

    Map<String, String> aliases = KafkaMessageBrokerConfig.topicAliases(properties);

    assertThat(aliases)
        .containsEntry("high-priority-topic", "custom-high-topic")
        .containsEntry("medium-priority-topic", "custom-medium-topic")
        .containsEntry("low-priority-topic", "custom-low-topic");
  }

  @Test
  void topicAliases_defaultTopics_mapNameToItself() {
    NotificationKafkaProperties properties = new NotificationKafkaProperties(
        new NotificationKafkaProperties.Consumer("notification-service"),
        new NotificationKafkaProperties.Topics());

    Map<String, String> aliases = KafkaMessageBrokerConfig.topicAliases(properties);

    assertThat(aliases)
        .containsEntry("high-priority-topic", "high-priority-topic")
        .containsEntry("medium-priority-topic", "medium-priority-topic")
        .containsEntry("low-priority-topic", "low-priority-topic");
  }

  @Test
  void deadLetterQueuePort_isKafkaAdapterReusingMessageBrokerProducer() {
    KafkaTemplate<String, Map<String, Object>> template =
        new KafkaTemplate<>(config.messageBrokerProducerFactory());
    NotificationKafkaProperties properties = new NotificationKafkaProperties(
        new NotificationKafkaProperties.Consumer("notification-service"),
        new NotificationKafkaProperties.Topics());

    DeadLetterQueuePort port =
        config.deadLetterQueuePort(template, properties, new SimpleMeterRegistry());

    assertThat(port).isInstanceOf(KafkaDeadLetterQueueAdapter.class);
  }
}
