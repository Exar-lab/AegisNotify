package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.NotificationSummary;
import com.aegisnotify.notification.application.port.in.GetNotificationsByChannelUseCase;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.model.Notification;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GetNotificationsByChannelService implements GetNotificationsByChannelUseCase {

  private final NotificationRepository notificationRepository;

  public GetNotificationsByChannelService(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  @Override
  public List<NotificationSummary> getByChannel(Channel channel) {
    return notificationRepository.findByChannel(channel).stream()
        .map(this::toSummary)
        .toList();
  }

  private NotificationSummary toSummary(Notification notification) {
    return new NotificationSummary(
        notification.getId(),
        notification.getChannel(),
        notification.getRecipient(),
        notification.getTemplateName(),
        notification.getStatus(),
        notification.getPriority(),
        notification.getCreatedAt()
    );
  }
}
