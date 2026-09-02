package com.aegisnotify.notification.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.NotificationServiceApplication;
import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.OutboxStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.infrastructure.persistence.entity.NotificationJpaEntity;
import com.aegisnotify.notification.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataNotificationRepository;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end proof for issue #32: a PENDING outbox event actually gets
 * published and marked {@code PROCESSED} once {@link OutboxWorkerScheduler}
 * fires, with no manual trigger — the relay activates the previously-dormant
 * Transactional Outbox pattern.
 *
 * <p>Docker is unreachable in some sandboxes (same pre-existing limitation
 * documented for {@code KafkaMessageBrokerAdapterIntegrationTest} and
 * {@code KafkaNotificationConsumerIntegrationTest} in Slice 0a) — this test
 * follows the same Testcontainers pattern as those and is expected to pass
 * wherever a real Docker daemon is available.</p>
 */
@SpringBootTest(classes = NotificationServiceApplication.class)
@Testcontainers
class OutboxWorkerSchedulerIntegrationTest {

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
    // The relay is the thing under test: enabled, polling fast enough for a test timeout.
    registry.add("notification.kafka.relay.enabled", () -> true);
    registry.add("notification.kafka.relay.poll-interval", () -> "PT1S");
    registry.add("notification.kafka.consumer.enabled", () -> false);
    registry.add("notification.providers.email.api-key", () -> "test-sendgrid-key");
    registry.add("notification.providers.sms.account-sid", () -> "test-account-sid");
    registry.add("notification.providers.sms.auth-token", () -> "test-auth-token");
    registry.add("notification.providers.whatsapp.account-sid", () -> "test-account-sid");
    registry.add("notification.providers.whatsapp.auth-token", () -> "test-auth-token");
    registry.add("notification.providers.push.project-id", () -> "test-project-id");
    registry.add("notification.providers.push.access-token", () -> "test-access-token");
  }

  @Autowired
  private SpringDataNotificationRepository notificationRepository;

  @Autowired
  private SpringDataOutboxEventRepository outboxEventRepository;

  // No production DeadLetterQueuePort implementation exists yet; every
  // context that boots the full NotificationServiceApplication (this one
  // included, since ConsumeNotificationEventService is an unconditional
  // @Service regardless of notification.kafka.consumer.enabled) must supply
  // one. Never invoked here — notification.kafka.consumer.enabled=false
  // keeps the actual Kafka consumer listener off, this only satisfies DI.
  @MockitoBean
  private DeadLetterQueuePort deadLetterQueuePort;

  @Test
  void relayTicks_publishesPendingOutboxEventAndMarksProcessed() throws InterruptedException {
    UUID notificationId = UUID.randomUUID();
    NotificationJpaEntity notification = new NotificationJpaEntity(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "Jane"), Priority.HIGH, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now());
    notificationRepository.save(notification);

    Map<String, Object> payload = new HashMap<>();
    payload.put("id", notificationId.toString());
    payload.put("channel", Channel.EMAIL.name());
    payload.put("recipient", "user@example.com");
    payload.put("templateName", "welcome");
    payload.put("parameters", Map.of("name", "Jane"));
    payload.put("priority", Priority.HIGH.name());

    OutboxEventJpaEntity event = new OutboxEventJpaEntity(
        UUID.randomUUID(), notificationId, payload, OutboxStatus.UNPROCESSED,
        0, Instant.now(), null);
    outboxEventRepository.save(event);

    // No manual trigger here on purpose: only OutboxWorkerScheduler's own
    // @Scheduled tick is allowed to move this row, proving the relay is live.
    OutboxEventJpaEntity persisted = awaitValue(Duration.ofSeconds(15),
        () -> outboxEventRepository.findById(event.getId()).orElseThrow(),
        e -> e.getStatus() == OutboxStatus.PROCESSED);
    assertThat(persisted.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
    assertThat(persisted.getProcessedAt()).isNotNull();

    NotificationJpaEntity updatedNotification = awaitValue(Duration.ofSeconds(15),
        () -> notificationRepository.findById(notificationId).orElseThrow(),
        n -> n.getStatus() == NotificationStatus.QUEUED);
    assertThat(updatedNotification.getStatus()).isEqualTo(NotificationStatus.QUEUED);
  }

  private static <T> T awaitValue(Duration timeout, Supplier<T> supplier,
      java.util.function.Predicate<T> condition) throws InterruptedException {
    Instant deadline = Instant.now().plus(timeout);
    T value = supplier.get();
    while (!condition.test(value) && Instant.now().isBefore(deadline)) {
      Thread.sleep(250);
      value = supplier.get();
    }
    return value;
  }
}
