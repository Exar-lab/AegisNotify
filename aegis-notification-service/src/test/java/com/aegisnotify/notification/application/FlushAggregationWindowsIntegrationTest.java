package com.aegisnotify.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.NotificationServiceApplication;
import com.aegisnotify.notification.application.dto.SummarizedContent;
import com.aegisnotify.notification.application.port.in.FlushAggregationWindowsUseCase;
import com.aegisnotify.notification.application.port.out.AggregationBufferRepository;
import com.aegisnotify.notification.application.port.out.AggregationSummarizerPort;
import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.domain.enums.AggregationBufferStatus;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.OutboxStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.BufferedNotification;
import com.aegisnotify.notification.infrastructure.persistence.entity.AggregationBufferJpaEntity;
import com.aegisnotify.notification.infrastructure.persistence.entity.NotificationJpaEntity;
import com.aegisnotify.notification.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.aegisnotify.notification.infrastructure.persistence.entity.TemplateJpaEntity;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataAggregationBufferRepository;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataNotificationRepository;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataTemplateRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end proof of the X2/B3 aggregate-success path (issue #86, Slice 2,
 * task 2.18) against a real Postgres: two buffered notifications sharing a
 * group render through the REAL {@code MustacheTemplateRenderer} and flush
 * to exactly one aggregate outbox event, with {@code aggregation_id} linking
 * both notifications and {@code aggregate_body} set on the leader only. Only
 * {@link AggregationSummarizerPort} is mocked (there is no real LLM
 * endpoint in a test environment) — the summarizer's own HTTP/breaker
 * behavior is already fully covered by {@code
 * AnthropicMessagesSummarizerAdapterTest} (MockWebServer).
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
class FlushAggregationWindowsIntegrationTest {

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
    registry.add("notification.aggregation.enabled", () -> true);
    registry.add("notification.providers.email.api-key", () -> "test-sendgrid-key");
    registry.add("notification.providers.sms.account-sid", () -> "test-account-sid");
    registry.add("notification.providers.sms.auth-token", () -> "test-auth-token");
    registry.add("notification.providers.whatsapp.account-sid", () -> "test-account-sid");
    registry.add("notification.providers.whatsapp.auth-token", () -> "test-auth-token");
    registry.add("notification.providers.push.project-id", () -> "test-project-id");
    registry.add("notification.providers.push.access-token", () -> "test-access-token");
  }

  @Autowired
  private FlushAggregationWindowsUseCase flushAggregationWindowsUseCase;

  @Autowired
  private SpringDataNotificationRepository notificationJpaRepository;

  @Autowired
  private SpringDataTemplateRepository templateJpaRepository;

  @Autowired
  private SpringDataOutboxEventRepository outboxEventJpaRepository;

  @Autowired
  private AggregationBufferRepository aggregationBufferRepository;

  @Autowired
  private SpringDataAggregationBufferRepository aggregationBufferJpaRepository;

  @MockitoBean
  private AggregationSummarizerPort summarizerPort;

  // No production DeadLetterQueuePort implementation exists yet; every
  // context that boots the full NotificationServiceApplication must supply
  // one to satisfy ConsumeNotificationEventService's dependencies.
  @MockitoBean
  private DeadLetterQueuePort deadLetterQueuePort;

  // notification.aggregation.enabled=true also activates the REAL
  // AggregationWindowScheduler, whose first @Scheduled tick fires
  // immediately on context startup regardless of poll-interval — racing
  // this test's own manual flushExpiredWindows() call on the exact same
  // buffered rows under real CI/Docker timing (a background tick can claim
  // and resolve a row before or during the test's own call, leaving the
  // manually-invoked call's view of "what got flushed" inconsistent with
  // what's actually in the database by assertion time). Mocking the
  // scheduler infrastructure eliminates the race outright.
  @MockitoBean
  private TaskScheduler taskScheduler;

  @Test
  void flushExpiredWindows_twoNotificationsSharingGroup_writesOneAggregateOutboxEvent() {
    Instant now = Instant.now();
    templateJpaRepository.save(new TemplateJpaEntity(
        UUID.randomUUID(), "welcome", Channel.EMAIL, "Welcome", "Hello {{name}}",
        List.of("name"), true, now, now));

    UUID leaderId = UUID.randomUUID();
    UUID siblingId = UUID.randomUUID();
    notificationJpaRepository.save(new NotificationJpaEntity(
        leaderId, Channel.EMAIL, "user@example.com", "welcome", Map.of("name", "Jane"),
        Priority.MEDIUM, NotificationStatus.PENDING, null, null,
        now.minusSeconds(20), now.minusSeconds(20)));
    notificationJpaRepository.save(new NotificationJpaEntity(
        siblingId, Channel.EMAIL, "user@example.com", "welcome", Map.of("name", "Jane"),
        Priority.MEDIUM, NotificationStatus.PENDING, null, null,
        now.minusSeconds(10), now.minusSeconds(10)));

    BufferedNotification leaderBufferRow = aggregationBufferRepository.save(
        BufferedNotification.create(leaderId, Channel.EMAIL, "user@example.com", "welcome",
            Priority.MEDIUM, now.minusSeconds(1), now.minusSeconds(20)));
    BufferedNotification siblingBufferRow = aggregationBufferRepository.save(
        BufferedNotification.create(siblingId, Channel.EMAIL, "user@example.com", "welcome",
            Priority.MEDIUM, now.minusSeconds(1), now.minusSeconds(10)));

    when(summarizerPort.summarize(any())).thenReturn(
        new SummarizedContent("Update", "Two things happened to Jane."));

    int resolved = flushAggregationWindowsUseCase.flushExpiredWindows();

    assertThat(resolved).isEqualTo(2);

    // TEMP DIAGNOSTIC (issue #86 flake investigation) - dump full state
    // regardless of status before asserting, so a failure tells us WHAT
    // happened instead of just that something did not match.
    System.out.println("=== DIAGNOSTIC: all outbox_events rows ===");
    outboxEventJpaRepository.findAll().forEach(e -> System.out.println(
        "outboxEvent id=" + e.getId() + " notificationId=" + e.getNotificationId()
            + " status=" + e.getStatus() + " createdAt=" + e.getCreatedAt()
            + " processedAt=" + e.getProcessedAt()));
    System.out.println("=== DIAGNOSTIC: all aggregation_buffer rows ===");
    aggregationBufferJpaRepository.findAll().forEach(e -> System.out.println(
        "bufferRow id=" + e.getId() + " notificationId=" + e.getNotificationId()
            + " status=" + e.getStatus() + " attempts=" + e.getAttempts()));
    System.out.println("=== DIAGNOSTIC: all notifications rows ===");
    notificationJpaRepository.findAll().forEach(e -> System.out.println(
        "notification id=" + e.getId() + " status=" + e.getStatus()
            + " aggregationId=" + e.getAggregationId()));

    List<OutboxEventJpaEntity> outboxRows = outboxEventJpaRepository.findByStatus(
        OutboxStatus.UNPROCESSED);
    assertThat(outboxRows).hasSize(1);
    assertThat(outboxRows.get(0).getNotificationId()).isEqualTo(leaderId);

    NotificationJpaEntity leader = notificationJpaRepository.findById(leaderId).orElseThrow();
    NotificationJpaEntity sibling = notificationJpaRepository.findById(siblingId).orElseThrow();
    assertThat(leader.getAggregationId()).isNotNull();
    assertThat(leader.getAggregateBody()).isEqualTo("Two things happened to Jane.");
    assertThat(sibling.getAggregationId()).isEqualTo(leader.getAggregationId());
    assertThat(sibling.getAggregateBody()).isNull();

    AggregationBufferJpaEntity leaderBufferAfter =
        aggregationBufferJpaRepository.findById(leaderBufferRow.getId()).orElseThrow();
    AggregationBufferJpaEntity siblingBufferAfter =
        aggregationBufferJpaRepository.findById(siblingBufferRow.getId()).orElseThrow();
    assertThat(leaderBufferAfter.getStatus()).isEqualTo(AggregationBufferStatus.DONE);
    assertThat(siblingBufferAfter.getStatus()).isEqualTo(AggregationBufferStatus.DONE);
  }
}
