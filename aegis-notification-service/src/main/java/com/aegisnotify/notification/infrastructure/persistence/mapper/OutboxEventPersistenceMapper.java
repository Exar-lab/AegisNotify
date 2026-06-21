package com.aegisnotify.notification.infrastructure.persistence.mapper;

import com.aegisnotify.notification.domain.model.OutboxEvent;
import com.aegisnotify.notification.infrastructure.persistence.entity.OutboxEventJpaEntity;
import java.util.HashMap;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventPersistenceMapper {

  public OutboxEvent toDomain(OutboxEventJpaEntity entity) {
    return OutboxEvent.reconstitute(
        entity.getId(),
        entity.getNotificationId(),
        new HashMap<>(entity.getPayload()),
        entity.getStatus(),
        entity.getRetryCount(),
        entity.getCreatedAt(),
        entity.getProcessedAt()
    );
  }

  public OutboxEventJpaEntity toJpa(OutboxEvent domain) {
    return new OutboxEventJpaEntity(
        domain.getId(),
        domain.getNotificationId(),
        new HashMap<>(domain.getPayload()),
        domain.getStatus(),
        domain.getRetryCount(),
        domain.getCreatedAt(),
        domain.getProcessedAt()
    );
  }
}
