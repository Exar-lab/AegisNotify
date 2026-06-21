package com.aegisnotify.notification.infrastructure.persistence.mapper;

import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.infrastructure.persistence.entity.NotificationJpaEntity;
import java.util.HashMap;
import org.springframework.stereotype.Component;

@Component
public class NotificationPersistenceMapper {

  public Notification toDomain(NotificationJpaEntity entity) {
    return Notification.reconstitute(
        entity.getId(),
        entity.getChannel(),
        entity.getRecipient(),
        entity.getTemplateName(),
        new HashMap<>(entity.getParameters()),
        entity.getPriority(),
        entity.getStatus(),
        entity.getProviderUsed(),
        entity.getErrorDetail(),
        entity.getCreatedAt(),
        entity.getUpdatedAt()
    );
  }

  public NotificationJpaEntity toJpa(Notification domain) {
    return new NotificationJpaEntity(
        domain.getId(),
        domain.getChannel(),
        domain.getRecipient(),
        domain.getTemplateName(),
        new HashMap<>(domain.getParameters()),
        domain.getPriority(),
        domain.getStatus(),
        domain.getProviderUsed(),
        domain.getErrorDetail(),
        domain.getCreatedAt(),
        domain.getUpdatedAt()
    );
  }
}
