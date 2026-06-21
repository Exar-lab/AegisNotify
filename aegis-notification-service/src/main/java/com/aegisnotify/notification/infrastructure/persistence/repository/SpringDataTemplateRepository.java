package com.aegisnotify.notification.infrastructure.persistence.repository;

import com.aegisnotify.notification.infrastructure.persistence.entity.TemplateJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataTemplateRepository
    extends JpaRepository<TemplateJpaEntity, UUID> {

  Optional<TemplateJpaEntity> findByNameAndActiveTrue(String name);
}
