package com.aegisnotify.notification.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.NotificationServiceApplication;
import com.aegisnotify.notification.application.dto.NotificationEvent;
import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.application.port.out.MessageBrokerPort;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.infrastructure.persistence.adapter.AggregationBufferRepositoryAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.NotificationLogRepositoryAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.NotificationRepositoryAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.OutboxEventRepositoryAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.TemplateRepositoryAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Testcontainers integration test for {@link KafkaMessageBrokerAdapter}
 * wired via {@link com.aegisnotify.notification.infrastructure.config.KafkaMessageBrokerConfig}
 * (issue #27 acceptance criterion).
 *
 * <p>Proves K2 end-to-end: publishing to the logical topic names hardcoded in
 * {@code PublishOutboxEventTransactions.TOPIC_MAP} actually lands on the
 * <em>configured</em> {@code notification.kafka.topics.*} topics — which are
 * deliberately overridden here to different physical names — with the
 * correct partition key and a body deserializable as {@link NotificationEvent}.</p>
 */
@SpringBootTest(classes = NotificationServiceApplication.class)
@ActiveProfiles("test")
@Testcontainers
class KafkaMessageBrokerAdapterIntegrationTest {

  @Container
  static final KafkaContainer KAFKA = new KafkaContainer(
      DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("eureka.client.enabled", () -> false);
    registry.add("spring.cloud.discovery.enabled", () -> false);
    registry.add("notification.kafka.topics.high-priority", () -> "custom-high-priority-topic");
    registry.add("notification.kafka.topics.medium-priority", () -> "custom-medium-priority-topic");
    registry.add("notification.kafka.topics.low-priority", () -> "custom-low-priority-topic");
  }

  @MockitoBean
  private TemplateRepositoryAdapter templateRepositoryAdapter;

  @MockitoBean
  private NotificationRepositoryAdapter notificationRepositoryAdapter;

  @MockitoBean
  private NotificationLogRepositoryAdapter notificationLogRepositoryAdapter;

  @MockitoBean
  private OutboxEventRepositoryAdapter outboxEventRepositoryAdapter;

  // @ActiveProfiles("test") excludes JPA repository auto-configuration
  // entirely (application-test.yml), so every JPA-backed adapter bean must
  // be explicitly mocked here — same reason as the other 4 adapters above.
  // Added when issue #86 introduced this adapter after this test was
  // originally written for issue #27, alongside NotificationServiceContext
  // SmokeTest's identical fix.
  @MockitoBean
  private AggregationBufferRepositoryAdapter aggregationBufferRepositoryAdapter;

  @MockitoBean
  private NotificationProviderPort notificationProviderPort;

  @MockitoBean
  private DeadLetterQueuePort deadLetterQueuePort;

  @Autowired
  private MessageBrokerPort messageBrokerPort;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private Consumer<String, String> testConsumer;

  @AfterEach
  void tearDown() {
    if (testConsumer != null) {
      testConsumer.close();
    }
  }

  @Test
  void publish_highPriorityAlias_landsOnConfiguredHighPriorityTopic() throws Exception {
    assertRoutesToConfiguredTopic("high-priority-topic", "custom-high-priority-topic", "HIGH");
  }

  @Test
  void publish_mediumPriorityAlias_landsOnConfiguredMediumPriorityTopic() throws Exception {
    assertRoutesToConfiguredTopic(
        "medium-priority-topic", "custom-medium-priority-topic", "MEDIUM");
  }

  @Test
  void publish_lowPriorityAlias_landsOnConfiguredLowPriorityTopic() throws Exception {
    assertRoutesToConfiguredTopic("low-priority-topic", "custom-low-priority-topic", "LOW");
  }

  private void assertRoutesToConfiguredTopic(
      String logicalTopic, String configuredTopic, String priority) throws Exception {
    testConsumer = createConsumer();
    testConsumer.subscribe(List.of(configuredTopic));

    UUID notificationId = UUID.randomUUID();
    Map<String, Object> payload = Map.of(
        "id", notificationId.toString(),
        "channel", "EMAIL",
        "recipient", "user@example.com",
        "templateName", "welcome",
        "parameters", Map.of("name", "John"),
        "priority", priority
    );

    messageBrokerPort.publish(logicalTopic, payload);

    ConsumerRecords<String, String> records = pollUntilRecordsPresent(testConsumer);
    assertThat(records.count()).isEqualTo(1);

    ConsumerRecord<String, String> record = records.iterator().next();
    assertThat(record.topic()).isEqualTo(configuredTopic);
    assertThat(record.key()).isEqualTo(notificationId.toString());

    NotificationEvent event = objectMapper.readValue(record.value(), NotificationEvent.class);
    assertThat(event.id()).isEqualTo(notificationId);
    assertThat(event.channel()).isEqualTo("EMAIL");
    assertThat(event.recipient()).isEqualTo("user@example.com");
    assertThat(event.templateName()).isEqualTo("welcome");
    assertThat(event.priority()).isEqualTo(priority);
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
