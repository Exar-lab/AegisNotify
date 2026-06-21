package com.aegisnotify.notification.infrastructure.persistence.repository;

import com.aegisnotify.notification.infrastructure.persistence.entity.NotificationLogJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataNotificationLogRepository
    extends JpaRepository<NotificationLogJpaEntity, UUID> {

  List<NotificationLogJpaEntity> findByNotificationIdOrderByCreatedAtAsc(
      UUID notificationId);
}
