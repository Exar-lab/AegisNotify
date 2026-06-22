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
    var saved = springDataRepository.save(entity);
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
}
