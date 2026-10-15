package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.NotificationListQuery;
import com.aegisnotify.notification.application.dto.NotificationPage;
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
  void listPage_preservesTotalsAndProviderWithoutLoadingOtherPages() {
    var query = new NotificationListQuery(
        "welcome", null, Channel.EMAIL, Priority.HIGH, null, null, null, 2, 10);
    Instant now = Instant.now();
    var notification = Notification.reconstitute(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome", Map.of(),
        Priority.HIGH, NotificationStatus.SENT, "SendGrid", null, now, now);
    when(notificationRepository.searchPage(query))
        .thenReturn(new NotificationPage<>(List.of(notification), 21, 2, 10));
    var result = service.listPage(query);
    assertEquals(21, result.totalElements());
    assertEquals(2, result.page());
    assertEquals(10, result.size());
    assertEquals("SendGrid", result.content().getFirst().providerUsed());
    verify(notificationRepository).searchPage(query);
  }

  @Test
  void list_bothFilters_passesBothToRepositoryAndMapsSummaries() {
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    Notification notification = Notification.reconstitute(
        id, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "Jane"), Priority.HIGH, NotificationStatus.FAILED,
        null, null, now, now
    );
    when(notificationRepository.search(Channel.EMAIL, NotificationStatus.FAILED, 20))
        .thenReturn(List.of(notification));

    List<NotificationSummary> result = service.list(Channel.EMAIL, NotificationStatus.FAILED);

    verify(notificationRepository).search(Channel.EMAIL, NotificationStatus.FAILED, 20);
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
    when(notificationRepository.search(Channel.SMS, null, 20))
        .thenReturn(List.of());

    service.list(Channel.SMS, null);

    verify(notificationRepository).search(Channel.SMS, null, 20);
  }

  @Test
  void list_statusOnly_passesNullChannelToRepository() {
    when(notificationRepository.search(null, NotificationStatus.PENDING, 20))
        .thenReturn(List.of());

    service.list(null, NotificationStatus.PENDING);

    verify(notificationRepository).search(null, NotificationStatus.PENDING, 20);
  }

  @Test
  void list_noFilters_passesBothNullToRepository() {
    when(notificationRepository.search(null, null, 20))
        .thenReturn(List.of());

    List<NotificationSummary> result = service.list(null, null);

    verify(notificationRepository).search(null, null, 20);
    assertTrue(result.isEmpty());
  }

  @Test
  void list_withCustomLimit_propagatesLimitToRepository() {
    when(notificationRepository.search(Channel.PUSH, NotificationStatus.SENT, 5))
        .thenReturn(List.of());

    service.list(Channel.PUSH, NotificationStatus.SENT, 5);

    verify(notificationRepository).search(Channel.PUSH, NotificationStatus.SENT, 5);
  }
}
