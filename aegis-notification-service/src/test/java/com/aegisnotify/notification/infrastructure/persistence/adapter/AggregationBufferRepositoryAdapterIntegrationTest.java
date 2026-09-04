package com.aegisnotify.notification.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.NotificationServiceApplication;
import com.aegisnotify.notification.application.port.out.AggregationBufferRepository;
import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.domain.enums.AggregationBufferStatus;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.BufferedNotification;
import com.aegisnotify.notification.infrastructure.persistence.entity.NotificationJpaEntity;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataNotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * Proves the aggregation-buffer claim mechanism against a real Postgres —
 * {@code AggregationBufferRepositoryAdapterTest} mocks {@code
 * SpringDataAggregationBufferRepository} entirely and only proves the
 * adapter's contract with a stubbed conditional-update result, never the
 * actual JPQL/SQL correctness this class exists to verify (issue #86 Slice
 * 1, task 1.24 — was Docker-blocked in the original apply session).
 *
 * <p>Docker is unreachable in some sandboxes, same pre-existing limitation
 * documented for {@code KafkaMessageBrokerAdapterIntegrationTest}/{@code
 * OutboxWorkerSchedulerIntegrationTest} — this follows the identical
 * Testcontainers-Postgres pattern and is expected to pass wherever a real
 * Docker daemon is available.</p>
 *
 * <p>{@code @Transactional}: the adapter's {@code conditionalClaim}/{@code
 * markDone} queries are {@code @Modifying}, which JPA refuses to execute
 * outside an active transaction — production code always has one (owned by
 * {@code AggregationFlushTransactions}), but a test calling the adapter
 * directly needs its own. Spring's test-managed transaction also rolls back
 * after each method, so the three tests don't need to worry about sharing
 * state.</p>
 */
@SpringBootTest(classes = NotificationServiceApplication.class)
@Testcontainers
@Transactional
class AggregationBufferRepositoryAdapterIntegrationTest {

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
  private AggregationBufferRepository repository;

  @Autowired
  private SpringDataNotificationRepository notificationRepository;

  // No production DeadLetterQueuePort implementation exists yet; every
  // context that boots the full NotificationServiceApplication must supply
  // one to satisfy ConsumeNotificationEventService's dependencies, even
  // though nothing in this test exercises the Kafka consumer path.
  @MockitoBean
  private DeadLetterQueuePort deadLetterQueuePort;

  /**
   * {@code aggregation_buffer.notification_id} carries a foreign key to
   * {@code notifications} (V3 migration) — every buffered row in this test
   * needs a real backing notification row first, or Postgres rejects the
   * insert. Never caught locally since Docker was unavailable in the dev
   * sandbox; surfaced by CI, which does have Docker.
   */
  private UUID seedNotification(String recipient, Channel channel) {
    UUID notificationId = UUID.randomUUID();
    notificationRepository.save(new NotificationJpaEntity(
        notificationId, channel, recipient, "welcome", Map.of("name", "Jane"),
        Priority.MEDIUM, NotificationStatus.PENDING, null, null, Instant.now(), Instant.now()));
    return notificationId;
  }

  @Test
  void conditionalClaim_statusAlreadyChanged_updatesZeroRows_returnsEmpty() {
    Instant now = Instant.now();
    UUID notificationId = seedNotification("user@example.com", Channel.EMAIL);
    BufferedNotification buffered = repository.save(BufferedNotification.create(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, now.minusSeconds(1), now.minusSeconds(300)));

    // A concurrent claimer wins first: the row's real DB status is now
    // CLAIMED, but our in-memory copy still says BUFFERED (the expected
    // status the conditional UPDATE will check against).
    Optional<BufferedNotification> firstClaim = repository.claim(buffered, now);
    assertThat(firstClaim).isPresent();

    // A second claimer racing on the same stale (pre-claim) view of the row
    // must lose: the WHERE status = :expectedStatus predicate no longer
    // matches the real row (now CLAIMED, not BUFFERED), so 0 rows update.
    Optional<BufferedNotification> secondClaimOnStaleView = repository.claim(buffered, now);
    assertThat(secondClaimOnStaleView).isEmpty();
  }

  @Test
  void findClaimable_includesExpiredBufferedRow_excludesNotYetExpiredRow() {
    Instant now = Instant.now();
    BufferedNotification expired = repository.save(BufferedNotification.create(
        seedNotification("expired@example.com", Channel.EMAIL), Channel.EMAIL,
        "expired@example.com", "welcome", Priority.MEDIUM,
        now.minusSeconds(1), now.minusSeconds(300)));
    BufferedNotification notYetExpired = repository.save(BufferedNotification.create(
        seedNotification("future@example.com", Channel.EMAIL), Channel.EMAIL,
        "future@example.com", "welcome", Priority.MEDIUM,
        now.plusSeconds(300), now.minusSeconds(1)));

    List<BufferedNotification> claimable =
        repository.findClaimable(now, now.minus(java.time.Duration.ofMinutes(2)));

    assertThat(claimable).extracting(BufferedNotification::getId)
        .contains(expired.getId())
        .doesNotContain(notYetExpired.getId());
  }

  @Test
  void findClaimable_includesStaleClaimedRow_pastLeaseCutoff() {
    Instant now = Instant.now();
    BufferedNotification buffered = repository.save(BufferedNotification.create(
        seedNotification("stale@example.com", Channel.EMAIL), Channel.EMAIL,
        "stale@example.com", "welcome", Priority.MEDIUM,
        now.plusSeconds(300), now.minusSeconds(600)));
    Instant staleClaimTime = now.minus(java.time.Duration.ofMinutes(5));
    Optional<BufferedNotification> claimed = repository.claim(buffered, staleClaimTime);
    assertThat(claimed).isPresent();

    // Lease cutoff of 2 minutes: a row claimed 5 minutes ago is stale and
    // must be reclaimable, even though its window itself hasn't expired.
    Instant leaseCutoff = now.minus(java.time.Duration.ofMinutes(2));
    List<BufferedNotification> claimable = repository.findClaimable(now, leaseCutoff);

    assertThat(claimable).extracting(BufferedNotification::getId).contains(buffered.getId());
    assertThat(claimable.stream()
        .filter(b -> b.getId().equals(buffered.getId()))
        .findFirst()
        .orElseThrow()
        .getStatus())
        .isEqualTo(AggregationBufferStatus.CLAIMED);
  }
}
