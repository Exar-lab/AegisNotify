package com.aegisnotify.notification.infrastructure.persistence.adapter;

import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import com.aegisnotify.notification.infrastructure.persistence.mapper.OutboxEventPersistenceMapper;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventRepositoryAdapter implements OutboxEventRepository {

  private final SpringDataOutboxEventRepository springDataRepository;
  private final OutboxEventPersistenceMapper mapper;

  public OutboxEventRepositoryAdapter(
      SpringDataOutboxEventRepository springDataRepository,
      OutboxEventPersistenceMapper mapper) {
    this.springDataRepository = springDataRepository;
    this.mapper = mapper;
  }

  @Override
  public OutboxEvent save(OutboxEvent event) {
    var entity = mapper.toJpa(event);
    var saved = springDataRepository.save(entity);
    return mapper.toDomain(saved);
  }
}
