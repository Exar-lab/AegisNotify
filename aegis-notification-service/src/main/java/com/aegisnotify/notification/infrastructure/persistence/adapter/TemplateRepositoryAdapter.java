package com.aegisnotify.notification.infrastructure.persistence.adapter;

import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.domain.model.Template;
import com.aegisnotify.notification.infrastructure.persistence.mapper.TemplatePersistenceMapper;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataTemplateRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TemplateRepositoryAdapter implements TemplateRepository {

  private final SpringDataTemplateRepository springDataRepository;
  private final TemplatePersistenceMapper mapper;

  public TemplateRepositoryAdapter(
      SpringDataTemplateRepository springDataRepository,
      TemplatePersistenceMapper mapper) {
    this.springDataRepository = springDataRepository;
    this.mapper = mapper;
  }

  @Override
  public Optional<Template> findActiveByName(String name) {
    return springDataRepository.findByNameAndActiveTrue(name)
        .map(mapper::toDomain);
  }

  @Override
  public List<Template> findAll() {
    return springDataRepository.findAllByOrderByNameAsc().stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public Optional<Template> findById(UUID id) {
    return springDataRepository.findById(id).map(mapper::toDomain);
  }

  @Override
  public boolean existsByName(String name) {
    return springDataRepository.existsByName(name);
  }

  @Override
  public Template save(Template template) {
    return mapper.toDomain(springDataRepository.save(mapper.toJpa(template)));
  }
}
