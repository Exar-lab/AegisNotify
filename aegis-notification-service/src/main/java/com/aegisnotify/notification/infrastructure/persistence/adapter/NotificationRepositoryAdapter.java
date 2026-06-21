package com.aegisnotify.notification.infrastructure.persistence.adapter;

import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.infrastructure.persistence.mapper.NotificationPersistenceMapper;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataNotificationRepository;
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
    var saved = springDataRepository.save(entity);
    return mapper.toDomain(saved);
  }

  @Override
  public Optional<Notification> findById(UUID id) {
    return springDataRepository.findById(id)
        .map(mapper::toDomain);
  }
}
