package com.aegisnotify.notification.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.NotificationServiceApplication;
import com.aegisnotify.notification.application.dto.NotificationEvent;
import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.application.port.out.MessageBrokerPort;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.application.port.out.TemplateRenderer;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.infrastructure.persistence.entity.NotificationJpaEntity;
import com.aegisnotify.notification.infrastructure.persistence.entity.TemplateJpaEntity;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataNotificationRepository;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataTemplateRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = NotificationServiceApplication.class)
@Testcontainers
class KafkaNotificationConsumerIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
      DockerImageName.parse("postgres:16-alpine"))
      .withDatabaseName("aegisnotify")
      .withUsername("aegis")
      .withPassword("aegis");

  @Container
  static final KafkaContainer KAFKA = new KafkaContainer(
      DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("eureka.client.enabled", () -> false);
    registry.add("spring.cloud.discovery.enabled", () -> false);
    registry.add("audit.publishing.enabled", () -> false);
    registry.add("notification.kafka.consumer.enabled", () -> true);
    registry.add("notification.kafka.consumer.group-id", () -> "notification-service-it");
  }

  @TestConfiguration
  static class KafkaTestProducerConfig {

    @Bean
    KafkaTemplate<String, NotificationEvent> notificationEventKafkaTemplate(
        @Value("${spring.kafka.bootstrap-servers}")
        String bootstrapServers) {
      Map<String, Object> props = new java.util.HashMap<>();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
      props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
      ProducerFactory<String, NotificationEvent> producerFactory =
          new DefaultKafkaProducerFactory<>(props);
      return new KafkaTemplate<>(producerFactory);
    }
  }

  @Autowired
  private KafkaTemplate<String, NotificationEvent> notificationEventKafkaTemplate;

  @Autowired
  private SpringDataNotificationRepository notificationRepository;

  @Autowired
  private SpringDataTemplateRepository templateRepository;

  @MockitoBean
  private TemplateRenderer templateRenderer;

  @MockitoBean
  private NotificationProviderPort notificationProviderPort;

  @MockitoBean
  private DeadLetterQueuePort deadLetterQueuePort;

  @MockitoBean
  private MessageBrokerPort messageBrokerPort;

  @Test
  void publishMessage_consumerTransitionsNotificationToProcessing() throws Exception {
    UUID notificationId = UUID.randomUUID();
    TemplateJpaEntity template = new TemplateJpaEntity(
        UUID.randomUUID(), "welcome", Channel.EMAIL, "Hello", "Hi ${name}",
        List.of("name"), true, Instant.now(), Instant.now());
    NotificationJpaEntity notification = new NotificationJpaEntity(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now());

    templateRepository.save(template);
    notificationRepository.save(notification);

    CountDownLatch processingObserved = new CountDownLatch(1);
    Mockito.when(templateRenderer.render(Mockito.anyString(), Mockito.anyMap()))
        .thenReturn("rendered-body");
    Mockito.when(notificationProviderPort.send(Mockito.any(), Mockito.anyString(),
        Mockito.anyString(), Mockito.anyString())).thenAnswer(invocation -> {
          NotificationJpaEntity persisted = notificationRepository.findById(notificationId)
              .orElseThrow();
          assertThat(persisted.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
          processingObserved.countDown();
          return new ProviderResult(ProviderResult.Outcome.SENT, "test-provider", null);
        });

    NotificationEvent event = new NotificationEvent(
        notificationId, "EMAIL", "user@example.com", "welcome",
        Map.of("name", "John"), "HIGH"
    );

    notificationEventKafkaTemplate.send("high-priority-topic", notificationId.toString(), event)
        .get(10, TimeUnit.SECONDS);

    assertThat(processingObserved.await(10, TimeUnit.SECONDS)).isTrue();
  }
}
