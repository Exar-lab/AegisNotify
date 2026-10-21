package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.NotificationListQuery;
import com.aegisnotify.notification.application.dto.NotificationPage;
import com.aegisnotify.notification.application.dto.NotificationSummary;
import com.aegisnotify.notification.application.port.in.ListNotificationsUseCase;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.model.Notification;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListNotificationsService implements ListNotificationsUseCase {

  private final NotificationRepository notificationRepository;

  public ListNotificationsService(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  @Override
  public NotificationPage<NotificationSummary> listPage(
      NotificationListQuery query) {
    var result = notificationRepository.searchPage(query);
    return new NotificationPage<>(
        result.content().stream().map(this::toSummary).toList(),
        result.totalElements(), result.page(), result.size());
  }

  @Override
  public List<NotificationSummary> list(
      Channel channel, NotificationStatus status, Integer limit) {
    return notificationRepository.search(channel, status, limit).stream()
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
        notification.getCreatedAt(),
        notification.getProviderUsed()
    );
  }
}
