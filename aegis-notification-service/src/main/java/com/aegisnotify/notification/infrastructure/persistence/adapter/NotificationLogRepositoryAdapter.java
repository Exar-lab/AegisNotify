package com.aegisnotify.notification.infrastructure.persistence.adapter;

import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.infrastructure.persistence.mapper.NotificationLogPersistenceMapper;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataNotificationLogRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NotificationLogRepositoryAdapter
    implements NotificationLogRepository {

  private final SpringDataNotificationLogRepository springDataRepository;
  private final NotificationLogPersistenceMapper mapper;

  public NotificationLogRepositoryAdapter(
      SpringDataNotificationLogRepository springDataRepository,
      NotificationLogPersistenceMapper mapper) {
    this.springDataRepository = springDataRepository;
    this.mapper = mapper;
  }

  @Override
  public NotificationLog save(NotificationLog log) {
    var entity = mapper.toJpa(log);
    var saved = springDataRepository.save(entity);
    return mapper.toDomain(saved);
  }

  @Override
  public List<NotificationLog> findByNotificationIdOrderByCreatedAt(
      UUID notificationId) {
    return springDataRepository
        .findByNotificationIdOrderByCreatedAtAsc(notificationId)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }
}
