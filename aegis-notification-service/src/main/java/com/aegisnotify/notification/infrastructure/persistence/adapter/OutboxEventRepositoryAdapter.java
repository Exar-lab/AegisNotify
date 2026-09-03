package com.aegisnotify.notification.infrastructure.persistence.adapter;

import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.domain.enums.OutboxStatus;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import com.aegisnotify.notification.infrastructure.persistence.mapper.OutboxEventPersistenceMapper;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import java.util.List;
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
    // saveAndFlush, not save: see NotificationRepositoryAdapter.save for why
    // — this entity also carries a jsonb-typed column (payload), and is
    // exactly the write that was observed lost when issued right after a
    // notification update in the same transaction without an intervening
    // flush.
    var saved = springDataRepository.saveAndFlush(entity);
    return mapper.toDomain(saved);
  }

  @Override
  public List<OutboxEvent> findPendingEvents() {
    return springDataRepository.findByStatus(OutboxStatus.UNPROCESSED)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }
}
