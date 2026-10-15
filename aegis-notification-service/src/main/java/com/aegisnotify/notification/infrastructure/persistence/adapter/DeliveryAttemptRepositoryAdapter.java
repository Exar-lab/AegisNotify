package com.aegisnotify.notification.infrastructure.persistence.adapter;

import com.aegisnotify.notification.application.port.out.DeliveryAttemptRepository;
import com.aegisnotify.notification.domain.model.DeliveryAttempt;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryAttemptRepositoryAdapter implements DeliveryAttemptRepository {

  private final JdbcTemplate jdbc;

  public DeliveryAttemptRepositoryAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void saveAll(UUID notificationId, List<DeliveryAttempt> attempts) {
    jdbc.batchUpdate("""
        INSERT INTO delivery_attempts
            (id, notification_id, provider_name, provider_role, success,
             result_code, error_detail, latency_millis, attempted_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, attempts, 100, (statement, attempt) -> {
          statement.setObject(1, UUID.randomUUID());
          statement.setObject(2, notificationId);
          statement.setString(3, attempt.providerName());
          statement.setString(4, attempt.role());
          statement.setBoolean(5, attempt.success());
          statement.setString(6, attempt.code());
          statement.setString(7, attempt.errorDetail());
          statement.setLong(8, attempt.latencyMillis());
          statement.setTimestamp(9, Timestamp.from(attempt.timestamp()));
        });
  }

  @Override
  public List<DeliveryAttempt> findByNotificationId(UUID notificationId) {
    return jdbc.query("""
        SELECT provider_name, provider_role, success, result_code, error_detail,
               latency_millis, attempted_at
        FROM delivery_attempts WHERE notification_id = ? ORDER BY sequence_number
        """, (row, index) -> new DeliveryAttempt(
            row.getString("provider_name"), row.getString("provider_role"),
            row.getBoolean("success"), row.getString("result_code"),
            row.getString("error_detail"), row.getLong("latency_millis"),
            row.getTimestamp("attempted_at").toInstant()), notificationId);
  }
}
