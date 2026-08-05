package com.aegisnotify.notification.infrastructure.persistence.mapper;

import com.aegisnotify.notification.domain.model.Template;
import com.aegisnotify.notification.infrastructure.persistence.entity.TemplateJpaEntity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TemplatePersistenceMapper {

  public Template toDomain(TemplateJpaEntity entity) {
    List<String> variables = entity.getVariables() == null
        ? new ArrayList<>()
        : new ArrayList<>(entity.getVariables());
    return Template.reconstitute(
        entity.getId(),
        entity.getName(),
        entity.getChannel(),
        entity.getSubject(),
        entity.getBody(),
        variables,
        entity.isActive(),
        entity.getCreatedAt(),
        entity.getUpdatedAt()
    );
  }

  public TemplateJpaEntity toJpa(Template domain) {
    return new TemplateJpaEntity(
        domain.getId(),
        domain.getName(),
        domain.getChannel(),
        domain.getSubject(),
        domain.getBody(),
        new ArrayList<>(domain.getVariables()),
        domain.isActive(),
        domain.getCreatedAt(),
        domain.getUpdatedAt()
    );
  }
}
