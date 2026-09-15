package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.NotificationSummary;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.service.ListNotificationsService;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListNotificationsServiceTest {

  @Mock
  private NotificationRepository notificationRepository;

  @InjectMocks
  private ListNotificationsService service;

  @Test
  void list_bothFilters_passesBothToRepositoryAndMapsSummaries() {
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    Notification notification = Notification.reconstitute(
        id, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "Jane"), Priority.HIGH, NotificationStatus.FAILED,
        null, null, now, now
    );
    when(notificationRepository.search(Channel.EMAIL, NotificationStatus.FAILED))
        .thenReturn(List.of(notification));

    List<NotificationSummary> result = service.list(Channel.EMAIL, NotificationStatus.FAILED);

    verify(notificationRepository).search(Channel.EMAIL, NotificationStatus.FAILED);
    assertEquals(1, result.size());
    NotificationSummary summary = result.get(0);
    assertEquals(id, summary.id());
    assertEquals(Channel.EMAIL, summary.channel());
    assertEquals("user@example.com", summary.recipient());
    assertEquals("welcome", summary.templateName());
    assertEquals(NotificationStatus.FAILED, summary.status());
    assertEquals(Priority.HIGH, summary.priority());
    assertEquals(now, summary.createdAt());
  }

  @Test
  void list_channelOnly_passesNullStatusToRepository() {
    when(notificationRepository.search(Channel.SMS, null))
        .thenReturn(List.of());

    service.list(Channel.SMS, null);

    verify(notificationRepository).search(Channel.SMS, null);
  }

  @Test
  void list_statusOnly_passesNullChannelToRepository() {
    when(notificationRepository.search(null, NotificationStatus.PENDING))
        .thenReturn(List.of());

    service.list(null, NotificationStatus.PENDING);

    verify(notificationRepository).search(null, NotificationStatus.PENDING);
  }

  @Test
  void list_noFilters_passesBothNullToRepository() {
    when(notificationRepository.search(null, null))
        .thenReturn(List.of());

    List<NotificationSummary> result = service.list(null, null);

    verify(notificationRepository).search(null, null);
    assertTrue(result.isEmpty());
  }
}
