package com.aegisnotify.notification.infrastructure.persistence.repository;

import com.aegisnotify.notification.domain.enums.OutboxStatus;
import com.aegisnotify.notification.infrastructure.persistence.entity.OutboxEventJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOutboxEventRepository
    extends JpaRepository<OutboxEventJpaEntity, UUID> {

  List<OutboxEventJpaEntity> findByStatus(OutboxStatus status);
}
