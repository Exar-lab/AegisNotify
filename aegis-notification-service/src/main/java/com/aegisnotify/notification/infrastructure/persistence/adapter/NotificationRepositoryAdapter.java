package com.aegisnotify.notification.infrastructure.persistence.adapter;

import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.infrastructure.persistence.mapper.NotificationPersistenceMapper;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataNotificationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NotificationRepositoryAdapter implements NotificationRepository {

  private final SpringDataNotificationRepository springDataRepository;
  private final NotificationPersistenceMapper mapper;

  public NotificationRepositoryAdapter(
      SpringDataNotificationRepository springDataRepository,
      NotificationPersistenceMapper mapper) {
    this.springDataRepository = springDataRepository;
    this.mapper = mapper;
  }

  @Override
  public Notification save(Notification notification) {
    var entity = mapper.toJpa(notification);
    // saveAndFlush, not save: this entity carries a jsonb-typed column
    // (parameters). Writes to jsonb-typed entities issued back-to-back with
    // writes to other entity types in the same transaction (e.g. the
    // outbox event write that immediately follows a notification update in
    // AggregationFlushTransactions.flushAggregate) were silently lost —
    // never reaching the database — under Hibernate's deferred, batched
    // auto-flush-at-commit, while an eagerly flushed write in the same
    // transaction persisted correctly. Forcing an immediate flush per save
    // sidesteps that batching hazard.
    var saved = springDataRepository.saveAndFlush(entity);
    return mapper.toDomain(saved);
  }

  @Override
  public Optional<Notification> findById(UUID id) {
    return springDataRepository.findById(id)
        .map(mapper::toDomain);
  }

  @Override
  public List<Notification> findByStatus(NotificationStatus status) {
    return springDataRepository.findByStatus(status).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public List<Notification> findByChannel(Channel channel) {
    return springDataRepository.findByChannel(channel).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public List<Notification> findByAggregationId(UUID aggregationId) {
    return springDataRepository.findByAggregationId(aggregationId).stream()
        .map(mapper::toDomain)
        .toList();
  }
}
