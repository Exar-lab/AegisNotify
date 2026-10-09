package com.aegisnotify.notification.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.NotificationServiceApplication;
import com.aegisnotify.notification.application.dto.DashboardAggregate;
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
import org.springframework.transaction.annotation.Transactional;
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
 * <p>{@code @Transactional}: each test seeds its own rows into the shared container; without a
 * per-test rollback, {@code search}'s nullable filters would match rows left behind by earlier
 * tests, same rationale as {@code AggregationBufferRepositoryAdapterIT}.</p>
 *
 * <p>Docker is unreachable in some sandboxes, the same pre-existing limitation already
 * documented on {@code AggregationBufferRepositoryAdapterIT} and
 * {@code CancelRetryNotificationIT} (Slice A) — this follows the identical
 * Testcontainers-Postgres pattern and is expected to pass wherever a real Docker daemon is
 * available.</p>
 */
@SpringBootTest(classes = NotificationServiceApplication.class)
@Testcontainers
@Transactional
class NotificationRepositoryAdapterIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
      DockerImageName.parse("postgres:16-alpine"))
      .withDatabaseName("aegisnotify")
      .withUsername("aegis")
      .withPassword("aegis")
      .withReuse(true);

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

  @Test
  void aggregateSince_emptyWindow_returnsZeroCountsNotNull() {
    // The regression this test exists for: SQL SUM() over zero matching rows
    // is NULL, not 0. NotificationDashboardCountsProjection declares its
    // counts as primitive long, so an unguarded SUM blew up with
    // AopInvocationException ("Null return value ... does not match
    // primitive return type") the first time this endpoint was hit against
    // an empty table — caught manually against the real app, not by
    // GetDashboardSummaryServiceTest, since that test mocks the repository
    // and never exercises the real @Query.
    DashboardAggregate result = repository.aggregateSince(Instant.now().minusSeconds(3600));

    assertThat(result.totalCount()).isZero();
    assertThat(result.sentCount()).isZero();
    assertThat(result.sentViaFallbackCount()).isZero();
    assertThat(result.failedCriticalCount()).isZero();
    assertThat(result.avgTerminalLatencyMillis()).isNull();
  }

  @Test
  void aggregateSince_mixedOutcomes_countsEachBucketAndAveragesTerminalLatency() {
    Instant since = Instant.now().minusSeconds(3600);
    Instant createdAt = since.plusSeconds(60);
    seedNotificationWithTimestamps(
        Channel.EMAIL, NotificationStatus.SENT, createdAt, createdAt.plusMillis(500));
    seedNotificationWithTimestamps(
        Channel.SMS, NotificationStatus.SENT_VIA_FALLBACK, createdAt, createdAt.plusMillis(1500));
    seedNotificationWithTimestamps(
        Channel.WHATSAPP, NotificationStatus.FAILED_CRITICAL, createdAt, createdAt);
    seedNotificationWithTimestamps(
        Channel.PUSH, NotificationStatus.PENDING, createdAt, createdAt);
    // Outside the window — must not be counted.
    seedNotificationWithTimestamps(
        Channel.EMAIL, NotificationStatus.SENT, since.minusSeconds(60), since.minusSeconds(60));

    DashboardAggregate result = repository.aggregateSince(since);

    assertThat(result.totalCount()).isEqualTo(4);
    assertThat(result.sentCount()).isEqualTo(1);
    assertThat(result.sentViaFallbackCount()).isEqualTo(1);
    assertThat(result.failedCriticalCount()).isEqualTo(1);
    // Average of the two terminal (SENT / SENT_VIA_FALLBACK) latencies: 500ms and 1500ms.
    assertThat(result.avgTerminalLatencyMillis()).isEqualTo(1000.0);
  }

  private void seedNotificationWithTimestamps(
      Channel channel, NotificationStatus status, Instant createdAt, Instant updatedAt) {
    springDataRepository.save(new NotificationJpaEntity(
        UUID.randomUUID(), channel, "user@example.com", "welcome", Map.of("name", "Jane"),
        Priority.MEDIUM, status, null, null, createdAt, updatedAt));
  }
}
