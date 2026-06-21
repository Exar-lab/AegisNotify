package com.aegisnotify.notification.infrastructure.persistence.repository;

import com.aegisnotify.notification.infrastructure.persistence.entity.NotificationJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataNotificationRepository
    extends JpaRepository<NotificationJpaEntity, UUID> {
}
