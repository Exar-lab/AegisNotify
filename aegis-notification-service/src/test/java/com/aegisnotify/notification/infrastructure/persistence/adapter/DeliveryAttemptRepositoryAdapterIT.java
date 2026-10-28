package com.aegisnotify.notification.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.domain.model.DeliveryAttempt;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DeliveryAttemptRepositoryAdapterIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @Test
  void persistsAttemptsInInvocationOrderAndKeepsNotificationsIsolated() {
    var datasource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(datasource).load().migrate();
    var jdbc = new JdbcTemplate(datasource);
    var repository = new DeliveryAttemptRepositoryAdapter(jdbc);
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO notifications (id, channel, recipient, template_name, priority)
        VALUES (?, 'EMAIL', 'test@example.com', 'welcome', 'HIGH')
        """, id);
    Instant timestamp = Instant.parse("2026-10-14T12:00:00Z");
    var primary = new DeliveryAttempt("SendGrid", "PRIMARY", false, "503",
        "Unavailable", 31, timestamp);
    var fallback = new DeliveryAttempt("SendGrid", "FALLBACK", true, "202",
        null, 15, timestamp);

    repository.saveAll(id, List.of(primary, fallback));
    repository.saveAll(id, List.of(primary));

    assertThat(repository.findByNotificationId(id)).containsExactly(primary, fallback, primary);
    assertThat(repository.findByNotificationId(UUID.randomUUID())).isEmpty();
  }
}
