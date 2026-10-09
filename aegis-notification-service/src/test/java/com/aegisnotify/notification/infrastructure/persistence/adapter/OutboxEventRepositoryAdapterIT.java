package com.aegisnotify.notification.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.NotificationServiceApplication;
import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.OutboxStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves {@link OutboxEventRepositoryAdapter} against a real Postgres. A mocked Spring Data
 * repository could never catch the regression guarded here: the adapter uses
 * {@code saveAndFlush} because jsonb-typed writes issued back-to-back with writes to another
 * entity type in the same transaction were observed lost under Hibernate's deferred batching.
 *
 * <p>{@code @Transactional}: each test rolls back, so rows never leak between tests that share
 * the container. Persistence is verified through {@link JdbcTemplate}, which bypasses the
 * persistence context and does not trigger a Hibernate flush — if the adapter only called
 * {@code save}, the rows would not be visible to it.</p>
 */
@SpringBootTest(classes = NotificationServiceApplication.class)
@Testcontainers
@Transactional
class OutboxEventRepositoryAdapterIT {

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
  private OutboxEventRepository outboxRepository;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  // See NotificationRepositoryAdapterIT: no production DeadLetterQueuePort exists yet but the
  // full application context requires one.
  @MockitoBean
  private DeadLetterQueuePort deadLetterQueuePort;

  /** outbox_events.notification_id has a FK to notifications, so a parent row is required. */
  private UUID seedNotification() {
    Notification notification = Notification.create(
        Channel.EMAIL, "user@example.com", "welcome", Map.of("name", "Jane"), Priority.MEDIUM);
    return notificationRepository.save(notification).getId();
  }

  private OutboxEvent newEvent(UUID notificationId) {
    return OutboxEvent.create(notificationId, Map.of("channel", "EMAIL", "priority", "MEDIUM"));
  }

  @Test
  void save_persistsEvent_andItIsFindableAsPending() {
    UUID notificationId = seedNotification();
    OutboxEvent saved = outboxRepository.save(newEvent(notificationId));

    List<OutboxEvent> pending = outboxRepository.findPendingEvents();

    assertThat(pending).extracting(OutboxEvent::getId).contains(saved.getId());
    OutboxEvent found = pending.stream()
        .filter(e -> e.getId().equals(saved.getId()))
        .findFirst()
        .orElseThrow();
    assertThat(found.getNotificationId()).isEqualTo(notificationId);
    assertThat(found.getStatus()).isEqualTo(OutboxStatus.UNPROCESSED);
    assertThat(found.getPayload()).containsEntry("channel", "EMAIL");
  }

  @Test
  void findPendingEvents_returnsOnlyUnprocessedRows() {
    UUID notificationId = seedNotification();
    OutboxEvent pending = outboxRepository.save(newEvent(notificationId));
    OutboxEvent processed = outboxRepository.save(newEvent(notificationId).markProcessed());

    List<OutboxEvent> result = outboxRepository.findPendingEvents();

    assertThat(result).extracting(OutboxEvent::getId)
        .contains(pending.getId())
        .doesNotContain(processed.getId());
    assertThat(result).allMatch(e -> e.getStatus() == OutboxStatus.UNPROCESSED);
  }

  @Test
  void findPendingEvents_afterMarkingProcessed_eventNoLongerAppears() {
    UUID notificationId = seedNotification();
    OutboxEvent saved = outboxRepository.save(newEvent(notificationId));
    assertThat(outboxRepository.findPendingEvents())
        .extracting(OutboxEvent::getId).contains(saved.getId());

    OutboxEvent processed = outboxRepository.save(saved.markProcessed());

    assertThat(processed.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
    assertThat(outboxRepository.findPendingEvents())
        .extracting(OutboxEvent::getId).doesNotContain(saved.getId());
  }

  @Test
  void save_outboxEventAfterNotificationInSameTransaction_bothArePersisted() {
    // Regression guard for saveAndFlush: a notification update immediately followed by an
    // outbox write (jsonb columns on both) used to lose one of the writes.
    Notification notification = notificationRepository.save(Notification.create(
        Channel.SMS, "+34600000000", "welcome", Map.of("name", "Jane"), Priority.HIGH));
    Notification queued = notificationRepository.save(notification.markQueued());
    OutboxEvent event = outboxRepository.save(newEvent(queued.getId()));

    // JdbcTemplate sees only what was actually flushed to the database.
    Integer notificationRows = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM notifications WHERE id = ? AND status = 'QUEUED'",
        Integer.class, queued.getId());
    Integer outboxRows = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM outbox_events WHERE id = ? AND notification_id = ?",
        Integer.class, event.getId(), queued.getId());

    assertThat(notificationRows).isEqualTo(1);
    assertThat(outboxRows).isEqualTo(1);
  }
}
