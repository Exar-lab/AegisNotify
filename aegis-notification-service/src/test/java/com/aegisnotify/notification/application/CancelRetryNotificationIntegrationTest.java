package com.aegisnotify.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.NotificationServiceApplication;
import com.aegisnotify.notification.application.port.in.CancelNotificationUseCase;
import com.aegisnotify.notification.application.port.in.RetryFailedNotificationUseCase;
import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.OutboxStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.infrastructure.persistence.entity.NotificationJpaEntity;
import com.aegisnotify.notification.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataNotificationRepository;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves cancel/retry against a real Postgres. Cancelling a {@code PENDING}
 * row is the V4 regression test (issue #71): without the V4 migration, the
 * commit fails with a check-constraint violation on {@code
 * chk_notification_status} because {@code CANCELLED} was never a permitted
 * value. Retrying a {@code FAILED} row must persist {@code PENDING} and
 * republish a new outbox event.
 *
 * <p>Docker is unreachable in some sandboxes, same pre-existing limitation
 * documented for {@code KafkaMessageBrokerAdapterIntegrationTest}/{@code
 * OutboxWorkerSchedulerIntegrationTest}/{@code
 * AggregationBufferRepositoryAdapterIntegrationTest} — this follows the
 * identical Testcontainers-Postgres pattern and is expected to pass wherever
 * a real Docker daemon is available.</p>
 */
@SpringBootTest(classes = NotificationServiceApplication.class)
@Testcontainers
class CancelRetryNotificationIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
      DockerImageName.parse("postgres:16-alpine"))
      .withDatabaseName("aegisnotify")
      .withUsername("aegis")
      .withPassword("aegis");

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("eureka.client.enabled", () -> false);
    registry.add("spring.cloud.discovery.enabled", () -> false);
    registry.add("audit.publishing.enabled", () -> false);
    registry.add("notification.providers.email.api-key", () -> "test-sendgrid-key");
    registry.add("notification.providers.sms.account-sid", () -> "test-account-sid");
    registry.add("notification.providers.sms.auth-token", () -> "test-auth-token");
    registry.add("notification.providers.whatsapp.account-sid", () -> "test-account-sid");
    registry.add("notification.providers.whatsapp.auth-token", () -> "test-auth-token");
    registry.add("notification.providers.push.project-id", () -> "test-project-id");
    registry.add("notification.providers.push.access-token", () -> "test-access-token");
  }

  @Autowired
  private CancelNotificationUseCase cancelNotificationUseCase;

  @Autowired
  private RetryFailedNotificationUseCase retryFailedNotificationUseCase;

  @Autowired
  private SpringDataNotificationRepository notificationJpaRepository;

  @Autowired
  private SpringDataOutboxEventRepository outboxEventJpaRepository;

  // No production DeadLetterQueuePort implementation exists yet; every
  // context that boots the full NotificationServiceApplication must supply
  // one to satisfy ConsumeNotificationEventService's dependencies, even
  // though nothing in this test exercises the Kafka consumer path.
  @MockitoBean
  private DeadLetterQueuePort deadLetterQueuePort;

  private UUID seedNotification(NotificationStatus status) {
    UUID notificationId = UUID.randomUUID();
    Instant now = Instant.now();
    notificationJpaRepository.save(new NotificationJpaEntity(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "Jane"), Priority.MEDIUM, status, null, null, now, now));
    return notificationId;
  }

  @Test
  void cancel_pendingRow_persistsCancelled() {
    UUID notificationId = seedNotification(NotificationStatus.PENDING);

    var response = cancelNotificationUseCase.cancel(notificationId);

    assertThat(response.status()).isEqualTo(NotificationStatus.CANCELLED);
    NotificationJpaEntity persisted =
        notificationJpaRepository.findById(notificationId).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
  }

  @Test
  void retry_failedRow_persistsPendingAndRepublishesOutboxEvent() {
    UUID notificationId = seedNotification(NotificationStatus.FAILED);

    var response = retryFailedNotificationUseCase.retry(notificationId);

    assertThat(response.status()).isEqualTo(NotificationStatus.PENDING);
    NotificationJpaEntity persisted =
        notificationJpaRepository.findById(notificationId).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(NotificationStatus.PENDING);

    List<OutboxEventJpaEntity> pendingOutboxRows =
        outboxEventJpaRepository.findByStatus(OutboxStatus.UNPROCESSED);
    assertThat(pendingOutboxRows)
        .extracting(OutboxEventJpaEntity::getNotificationId)
        .contains(notificationId);
  }
}
