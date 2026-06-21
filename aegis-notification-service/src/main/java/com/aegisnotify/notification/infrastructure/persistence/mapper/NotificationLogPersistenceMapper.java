package com.aegisnotify.notification.infrastructure.persistence.mapper;

import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.infrastructure.persistence.entity.NotificationLogJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class NotificationLogPersistenceMapper {

  public NotificationLog toDomain(NotificationLogJpaEntity entity) {
    return NotificationLog.reconstitute(
        entity.getId(),
        entity.getNotificationId(),
        entity.getStatus(),
        entity.getDetails(),
        entity.getCreatedAt()
    );
  }

  public NotificationLogJpaEntity toJpa(NotificationLog domain) {
    return new NotificationLogJpaEntity(
        domain.getId(),
        domain.getNotificationId(),
        domain.getStatus(),
        domain.getDetails(),
        domain.getCreatedAt()
    );
  }
}
