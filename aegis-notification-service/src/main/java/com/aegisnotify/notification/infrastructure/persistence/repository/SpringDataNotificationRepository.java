package com.aegisnotify.notification.infrastructure.persistence.repository;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.infrastructure.persistence.entity.NotificationJpaEntity;
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
}
