package com.aegisnotify.notification.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.NotificationServiceApplication;
import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.application.port.out.MessageBrokerPort;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.infrastructure.persistence.adapter.AggregationBufferRepositoryAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.NotificationLogRepositoryAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.NotificationRepositoryAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.OutboxEventRepositoryAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.TemplateRepositoryAdapter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers integration test for {@link KafkaDeadLetterQueueAdapter}
 * wired via {@link com.aegisnotify.notification.infrastructure.config.KafkaMessageBrokerConfig}
 * (issue #30 acceptance criterion).
 *
 * <p>Proves end-to-end that {@code DeadLetterQueuePort#sendToDlq} actually
 * lands a message on the configured {@code notification.kafka.topics.dlq}
 * topic (overridden here to a custom name, mirroring
 * {@link KafkaMessageBrokerAdapterIT}'s override pattern), with
 * the notification id as the partition key and both the notification id and
 * the failure reason present as real Kafka headers.</p>
 */
@SpringBootTest(classes = NotificationServiceApplication.class)
@ActiveProfiles("test")
@Testcontainers
class KafkaDeadLetterQueueAdapterIT {

  @Container
  static final KafkaContainer KAFKA = new KafkaContainer(
      DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
      .withReuse(true);

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("eureka.client.enabled", () -> false);
    registry.add("spring.cloud.discovery.enabled", () -> false);
    registry.add("notification.kafka.topics.dlq", () -> "custom-notifications-dlq");
  }

  @MockitoBean
  private TemplateRepositoryAdapter templateRepositoryAdapter;

  @MockitoBean
  private NotificationRepositoryAdapter notificationRepositoryAdapter;

  @MockitoBean
  private NotificationLogRepositoryAdapter notificationLogRepositoryAdapter;

  @MockitoBean
  private OutboxEventRepositoryAdapter outboxEventRepositoryAdapter;

  @MockitoBean
  private AggregationBufferRepositoryAdapter aggregationBufferRepositoryAdapter;

  @MockitoBean
  private NotificationProviderPort notificationProviderPort;

  @MockitoBean
  private MessageBrokerPort messageBrokerPort;

  @Autowired
  private DeadLetterQueuePort deadLetterQueuePort;

  private Consumer<String, String> testConsumer;

  @AfterEach
  void tearDown() {
    if (testConsumer != null) {
      testConsumer.close();
    }
  }

  @Test
  void sendToDlq_publishesToConfiguredDlqTopicWithHeaders() {
    testConsumer = createConsumer();
    testConsumer.subscribe(List.of("custom-notifications-dlq"));

    UUID notificationId = UUID.randomUUID();
    Map<String, Object> payload = Map.of(
        "notificationId", notificationId.toString(),
        "channel", "EMAIL",
        "recipient", "user@example.com",
        "templateName", "welcome"
    );
    String reason = "Critical failure after processing";

    deadLetterQueuePort.sendToDlq(notificationId, payload, reason);

    ConsumerRecords<String, String> records = pollUntilRecordsPresent(testConsumer);
    assertThat(records.count()).isEqualTo(1);

    ConsumerRecord<String, String> record = records.iterator().next();
    assertThat(record.topic()).isEqualTo("custom-notifications-dlq");
    assertThat(record.key()).isEqualTo(notificationId.toString());

    Header notificationIdHeader = record.headers().lastHeader("notificationId");
    Header reasonHeader = record.headers().lastHeader("reason");
    assertThat(notificationIdHeader).isNotNull();
    assertThat(reasonHeader).isNotNull();
    assertThat(new String(notificationIdHeader.value(), StandardCharsets.UTF_8))
        .isEqualTo(notificationId.toString());
    assertThat(new String(reasonHeader.value(), StandardCharsets.UTF_8)).isEqualTo(reason);

    assertThat(record.value()).contains(notificationId.toString()).contains("EMAIL");
  }

  private static ConsumerRecords<String, String> pollUntilRecordsPresent(
      Consumer<String, String> consumer) {
    Instant deadline = Instant.now().plusSeconds(15);
    ConsumerRecords<String, String> records = ConsumerRecords.empty();
    while (records.isEmpty() && Instant.now().isBefore(deadline)) {
      records = consumer.poll(Duration.ofSeconds(2));
    }
    return records;
  }

  private static Consumer<String, String> createConsumer() {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    return new KafkaConsumer<>(props);
  }
}
