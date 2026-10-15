package com.aegisnotify.notification.infrastructure.persistence.adapter;

import com.aegisnotify.notification.application.dto.ProviderCallMetrics;
import com.aegisnotify.notification.application.port.out.ProviderCallMetricsPort;
import com.aegisnotify.notification.domain.enums.Channel;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProviderCallMetricsAdapter implements ProviderCallMetricsPort {
  private final JdbcTemplate jdbc;

  public ProviderCallMetricsAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<ProviderCallMetrics> aggregate(Instant from, Instant today, Instant to) {
    return jdbc.query("""
        SELECT n.channel, a.provider_role, COUNT(*) AS calls,
               COUNT(CASE WHEN a.attempted_at >= ? THEN 1 END) AS calls_today,
               COUNT(CASE WHEN a.success THEN 1 END) AS successes
        FROM delivery_attempts a JOIN notifications n ON n.id = a.notification_id
        WHERE a.attempted_at >= ? AND a.attempted_at < ?
          AND (a.result_code IS NULL OR a.result_code NOT IN ('CIRCUIT_OPEN', 'NOT_CONFIGURED'))
        GROUP BY n.channel, a.provider_role
        """, (row, index) -> new ProviderCallMetrics(
            Channel.valueOf(row.getString("channel")), row.getString("provider_role"),
            row.getLong("calls"), row.getLong("calls_today"), row.getLong("successes")),
        Timestamp.from(today), Timestamp.from(from), Timestamp.from(to));
  }
}
