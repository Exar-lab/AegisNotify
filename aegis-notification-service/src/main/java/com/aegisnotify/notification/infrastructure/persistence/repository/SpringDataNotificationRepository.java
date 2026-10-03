package com.aegisnotify.notification.infrastructure.persistence.repository;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.infrastructure.persistence.entity.NotificationJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataNotificationRepository
    extends JpaRepository<NotificationJpaEntity, UUID> {

  List<NotificationJpaEntity> findByStatus(NotificationStatus status);

  List<NotificationJpaEntity> findByChannel(Channel channel);

  List<NotificationJpaEntity> findByAggregationId(UUID aggregationId);

  @Query("SELECT n FROM NotificationJpaEntity n WHERE "
      + "(:channel IS NULL OR n.channel = :channel) AND "
      + "(:status IS NULL OR n.status = :status) "
      + "ORDER BY n.createdAt DESC")
  List<NotificationJpaEntity> search(@Param("channel") Channel channel,
      @Param("status") NotificationStatus status);

  @Query("SELECT COUNT(n) AS totalCount, "
      // SQL SUM() over zero matching rows is NULL, not 0 — COALESCE guards
      // against that (an empty/quiet window), since the projection
      // interface declares these as primitive long, which can't hold null.
      + "COALESCE(SUM(CASE WHEN n.status = "
      + "    com.aegisnotify.notification.domain.enums.NotificationStatus.SENT "
      + "    THEN 1L ELSE 0L END), 0L) AS sentCount, "
      + "COALESCE(SUM(CASE WHEN n.status = "
      + "    com.aegisnotify.notification.domain.enums.NotificationStatus.SENT_VIA_FALLBACK "
      + "    THEN 1L ELSE 0L END), 0L) AS sentViaFallbackCount, "
      + "COALESCE(SUM(CASE WHEN n.status = "
      + "    com.aegisnotify.notification.domain.enums.NotificationStatus.FAILED_CRITICAL "
      + "    THEN 1L ELSE 0L END), 0L) AS failedCriticalCount "
      + "FROM NotificationJpaEntity n WHERE n.createdAt >= :since")
  NotificationDashboardCountsProjection countByStatusSince(@Param("since") Instant since);

  @Query("SELECT n.createdAt AS createdAt, n.updatedAt AS updatedAt "
      + "FROM NotificationJpaEntity n "
      + "WHERE n.createdAt >= :since AND n.status IN ("
      + "    com.aegisnotify.notification.domain.enums.NotificationStatus.SENT, "
      + "    com.aegisnotify.notification.domain.enums.NotificationStatus.SENT_VIA_FALLBACK)")
  List<NotificationLatencyProjection> findTerminalTimestampsSince(@Param("since") Instant since);
}
