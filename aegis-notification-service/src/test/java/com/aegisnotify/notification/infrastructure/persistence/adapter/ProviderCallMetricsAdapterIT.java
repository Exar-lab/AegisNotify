package com.aegisnotify.notification.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.application.dto.ProviderCallMetrics;
import com.aegisnotify.notification.domain.enums.Channel;
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
class ProviderCallMetricsAdapterIT {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @Test
  void aggregatesByChannelAndRoleWithUtcAndRollingBoundariesExcludingRejectedCalls() {
    var datasource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(datasource).load().migrate();
    var jdbc = new JdbcTemplate(datasource);
    var attempts = new DeliveryAttemptRepositoryAdapter(jdbc);
    var metrics = new ProviderCallMetricsAdapter(jdbc);
    UUID email = notification(jdbc, "EMAIL");
    UUID sms = notification(jdbc, "SMS");
    Instant now = Instant.parse("2026-10-15T12:00:00Z");
    Instant today = Instant.parse("2026-10-15T00:00:00Z");
    Instant from = now.minusSeconds(86400);
    attempts.saveAll(email, List.of(
        attempt("PRIMARY", true, null, from),
        attempt("PRIMARY", false, "503", today),
        attempt("PRIMARY", true, "202", from.minusSeconds(1)),
        attempt("PRIMARY", true, "202", now),
        attempt("PRIMARY", false, "CIRCUIT_OPEN", today),
        attempt("FALLBACK", false, "NOT_CONFIGURED", today),
        attempt("FALLBACK", true, "202", today)));
    attempts.saveAll(sms, List.of(attempt("PRIMARY", true, "202", today)));

    assertThat(metrics.aggregate(from, today, now)).containsExactlyInAnyOrder(
        new ProviderCallMetrics(Channel.EMAIL, "PRIMARY", 2, 1, 1),
        new ProviderCallMetrics(Channel.EMAIL, "FALLBACK", 1, 1, 1),
        new ProviderCallMetrics(Channel.SMS, "PRIMARY", 1, 1, 1));
    assertThat(metrics.aggregate(now.plusSeconds(1), now, now.plusSeconds(2))).isEmpty();
  }

  private UUID notification(JdbcTemplate jdbc, String channel) {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO notifications (id, channel, recipient, template_name, priority)
        VALUES (?, ?, 'test@example.com', 'welcome', 'HIGH')
        """, id, channel);
    return id;
  }

  private DeliveryAttempt attempt(String role, boolean success, String code, Instant timestamp) {
    return new DeliveryAttempt("same-vendor", role, success, code, null, 15, timestamp);
  }
}
