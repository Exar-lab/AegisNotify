package com.aegisnotify.notification.infrastructure.persistence.mapper;

import com.aegisnotify.notification.domain.model.BufferedNotification;
import com.aegisnotify.notification.infrastructure.persistence.entity.AggregationBufferJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class AggregationBufferPersistenceMapper {

  public BufferedNotification toDomain(AggregationBufferJpaEntity entity) {
    return BufferedNotification.reconstitute(
        entity.getId(),
        entity.getNotificationId(),
        entity.getChannel(),
        entity.getRecipient(),
        entity.getTemplateName(),
        entity.getPriority(),
        entity.getStatus(),
        entity.getExpiresAt(),
        entity.getClaimedAt(),
        entity.getAttempts(),
        entity.getCreatedAt()
    );
  }

  public AggregationBufferJpaEntity toJpa(BufferedNotification domain) {
    return new AggregationBufferJpaEntity(
        domain.getId(),
        domain.getNotificationId(),
        domain.getChannel(),
        domain.getRecipient(),
        domain.getTemplateName(),
        domain.getPriority(),
        domain.getStatus(),
        domain.getExpiresAt(),
        domain.getClaimedAt(),
        domain.getAttempts(),
        domain.getCreatedAt()
    );
  }
}
