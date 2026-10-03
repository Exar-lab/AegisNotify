package com.aegisnotify.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link KafkaProducerConfig} (audit event publishing).
 *
 * <p>Mirrors {@code KafkaMessageBrokerConfigTest}'s coverage of the sibling
 * outbox-relay producer: durability settings, wire-compatible serializers,
 * and the widened reconnect backoff.</p>
 */
class KafkaProducerConfigTest {

  private final KafkaProducerConfig config = new KafkaProducerConfig();

  {
    ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
  }

  @Test
  void auditProducerFactory_configuresDurabilitySettings() {
    ProducerFactory<String, AuditEventMessage> producerFactory = config.auditProducerFactory();

    Map<String, Object> props = producerFactory.getConfigurationProperties();

    assertThat(props.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
    assertThat(props.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isEqualTo(true);
  }

  @Test
  void auditProducerFactory_widensReconnectBackoffWithoutBoundingRetries() {
    // Same fix as messageBrokerProducerFactory: retries stay unbounded
    // (idempotence already implies that), only how often a reconnect to an
    // unreachable broker is attempted changes — from the client's default
    // 50ms/1000ms (a warning roughly once a second) to a capped exponential
    // backoff.
    ProducerFactory<String, AuditEventMessage> producerFactory = config.auditProducerFactory();

    Map<String, Object> props = producerFactory.getConfigurationProperties();

    assertThat(props.get(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG)).isEqualTo(1_000);
    assertThat(props.get(ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG)).isEqualTo(30_000);
  }

  @Test
  void auditProducerFactory_configuresWireCompatibleSerializers() {
    ProducerFactory<String, AuditEventMessage> producerFactory = config.auditProducerFactory();

    Map<String, Object> props = producerFactory.getConfigurationProperties();

    assertThat(props.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)).isEqualTo(
        StringSerializer.class);
    assertThat(props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(
        JsonSerializer.class);
    assertThat(props.get(JsonSerializer.ADD_TYPE_INFO_HEADERS)).isEqualTo(false);
  }

  @Test
  void auditProducerFactory_usesConfiguredBootstrapServers() {
    ProducerFactory<String, AuditEventMessage> producerFactory = config.auditProducerFactory();

    Map<String, Object> props = producerFactory.getConfigurationProperties();

    assertThat(props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("localhost:9092");
  }
}
