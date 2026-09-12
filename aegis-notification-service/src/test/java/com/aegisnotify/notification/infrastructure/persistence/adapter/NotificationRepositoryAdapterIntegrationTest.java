package com.aegisnotify.notification.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.NotificationServiceApplication;
import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.infrastructure.persistence.entity.NotificationJpaEntity;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataNotificationRepository;
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
 * Proves {@link NotificationRepositoryAdapter#search(Channel, NotificationStatus)} against a
 * real Postgres — this is the DO-5 nullable-bind regression test named in the design. A mocked
 * {@code SpringDataNotificationRepository} would only prove the adapter's delegation, never
 * whether the underlying {@code @Query} actually compiles and binds against Postgres, which is
 * the entire risk this test exists to catch.
 *
 * <p>Docker is unreachable in some sandboxes, the same pre-existing limitation already
 * documented on {@code AggregationBufferRepositoryAdapterIntegrationTest} and
 * {@code CancelRetryNotificationIntegrationTest} (Slice A) — this follows the identical
 * Testcontainers-Postgres pattern and is expected to pass wherever a real Docker daemon is
 * available.</p>
 */
@SpringBootTest(classes = NotificationServiceApplication.class)
@Testcontainers
class NotificationRepositoryAdapterIntegrationTest {

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
  private NotificationRepository repository;

  @Autowired
  private SpringDataNotificationRepository springDataRepository;

  // No production DeadLetterQueuePort implementation exists yet; every context that boots the
  // full NotificationServiceApplication must supply one to satisfy
  // ConsumeNotificationEventService's dependencies, even though nothing here exercises Kafka.
  @MockitoBean
  private DeadLetterQueuePort deadLetterQueuePort;

  private UUID seedNotification(Channel channel, NotificationStatus status, Instant createdAt) {
    UUID notificationId = UUID.randomUUID();
    springDataRepository.save(new NotificationJpaEntity(
        notificationId, channel, "user@example.com", "welcome", Map.of("name", "Jane"),
        Priority.MEDIUM, status, null, null, createdAt, createdAt));
    return notificationId;
  }

  @Test
  void search_bothFilters_returnsOnlyMatchingRows() {
    Instant now = Instant.now();
    UUID matching = seedNotification(Channel.EMAIL, NotificationStatus.FAILED, now);
    seedNotification(Channel.EMAIL, NotificationStatus.PENDING, now);
    seedNotification(Channel.SMS, NotificationStatus.FAILED, now);

    List<Notification> result = repository.search(Channel.EMAIL, NotificationStatus.FAILED);

    assertThat(result).extracting(Notification::getId).containsExactly(matching);
  }

  @Test
  void search_channelOnly_returnsRowsRegardlessOfStatus() {
    Instant now = Instant.now();
    UUID first = seedNotification(Channel.SMS, NotificationStatus.PENDING, now);
    UUID second = seedNotification(Channel.SMS, NotificationStatus.FAILED, now.plusSeconds(1));
    seedNotification(Channel.EMAIL, NotificationStatus.PENDING, now);

    List<Notification> result = repository.search(Channel.SMS, null);

    assertThat(result).extracting(Notification::getId).containsExactly(second, first);
  }

  @Test
  void search_statusOnly_returnsRowsRegardlessOfChannel() {
    Instant now = Instant.now();
    UUID first = seedNotification(Channel.EMAIL, NotificationStatus.PENDING, now);
    UUID second = seedNotification(Channel.SMS, NotificationStatus.PENDING, now.plusSeconds(1));
    seedNotification(Channel.EMAIL, NotificationStatus.FAILED, now);

    List<Notification> result = repository.search(null, NotificationStatus.PENDING);

    assertThat(result).extracting(Notification::getId).containsExactly(second, first);
  }

  @Test
  void search_noFilters_returnsAllRowsNewestFirst() {
    Instant now = Instant.now();
    UUID first = seedNotification(Channel.EMAIL, NotificationStatus.PENDING, now);
    UUID second = seedNotification(Channel.SMS, NotificationStatus.FAILED, now.plusSeconds(1));

    List<Notification> result = repository.search(null, null);

    assertThat(result).extracting(Notification::getId).containsExactly(second, first);
  }
}
