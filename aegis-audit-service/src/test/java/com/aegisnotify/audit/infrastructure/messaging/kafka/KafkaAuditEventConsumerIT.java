package com.aegisnotify.audit.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.aegisnotify.audit.AuditServiceApplication;
import com.aegisnotify.audit.application.dto.AuditEventCommand;
import com.aegisnotify.audit.application.port.out.AuditEventRepository;
import com.aegisnotify.audit.domain.enums.AuditStatus;
import com.aegisnotify.audit.domain.model.AuditTrail;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end check of consume, encrypt and persist: a real event published
 * to Kafka must end up as a trail in MongoDB via the real listener.
 */
@SpringBootTest(classes = AuditServiceApplication.class)
@Testcontainers
class KafkaAuditEventConsumerIT {

  private static final String TOPIC = "notification-audit-events";

  @Container
  static final KafkaContainer KAFKA = new KafkaContainer(
      DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
      .withReuse(true);

  @Container
  static final MongoDBContainer MONGO = new MongoDBContainer(
      DockerImageName.parse("mongo:7"))
      .withReuse(true);

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers",
        KAFKA::getBootstrapServers);
    registry.add("spring.data.mongodb.uri",
        () -> MONGO.getReplicaSetUrl("audit-it"));
    registry.add("eureka.client.enabled", () -> false);
    registry.add("spring.cloud.discovery.enabled", () -> false);
    registry.add("audit.consumer.group-id", () -> "audit-service-it");
  }

  @Autowired
  private AuditEventRepository auditEventRepository;

  @Test
  void publishedAuditEvent_isConsumedAndPersistedAsTrail() {
    UUID notificationId = UUID.randomUUID();
    AuditEventCommand command = new AuditEventCommand(
        notificationId, "SENT", "Delivered", "EMAIL",
        "user@example.com", "HIGH", Instant.now());

    KafkaTemplate<String, AuditEventCommand> template = producer();
    try {
      template.send(TOPIC, notificationId.toString(), command);
      template.flush();
    } finally {
      template.destroy();
    }

    await().atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(250))
        .untilAsserted(() -> {
          Optional<AuditTrail> trail =
              auditEventRepository.findByNotificationId(notificationId);
          assertThat(trail).isPresent();
          assertThat(trail.get().getCurrentStatus())
              .isEqualTo(AuditStatus.SENT);
          assertThat(trail.get().getEvents()).hasSize(1);
          // The recipient must be persisted encrypted, never in clear text.
          assertThat(trail.get().getEvents().get(0).getRecipient())
              .isNotEqualTo("user@example.com");
        });
  }

  private static KafkaTemplate<String, AuditEventCommand> producer() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
        KAFKA.getBootstrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        JsonSerializer.class);
    props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }
}
