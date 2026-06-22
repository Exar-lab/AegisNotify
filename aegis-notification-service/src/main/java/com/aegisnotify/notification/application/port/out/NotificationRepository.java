package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.model.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

  Notification save(Notification notification);

  Optional<Notification> findById(UUID id);

  List<Notification> findByStatus(NotificationStatus status);

  List<Notification> findByChannel(Channel channel);
}
