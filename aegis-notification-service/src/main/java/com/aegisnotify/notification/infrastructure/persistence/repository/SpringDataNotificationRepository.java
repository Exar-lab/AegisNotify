package com.aegisnotify.notification.infrastructure.persistence.repository;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.infrastructure.persistence.entity.NotificationJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataNotificationRepository
    extends JpaRepository<NotificationJpaEntity, UUID> {

  List<NotificationJpaEntity> findByStatus(NotificationStatus status);

  List<NotificationJpaEntity> findByChannel(Channel channel);
}
