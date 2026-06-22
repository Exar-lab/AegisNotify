package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.NotificationSummary;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.service.GetNotificationsByStatusService;
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
class GetNotificationsByStatusServiceTest {

  @Mock
  private NotificationRepository notificationRepository;

  @InjectMocks
  private GetNotificationsByStatusService service;

  @Test
  void getByStatus_found_returnsMappedSummaries() {
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();

    Notification notification = Notification.reconstitute(
        id, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH, NotificationStatus.PENDING,
        null, null, now, now
    );
    when(notificationRepository.findByStatus(NotificationStatus.PENDING))
        .thenReturn(List.of(notification));

    List<NotificationSummary> result = service.getByStatus(NotificationStatus.PENDING);

    assertEquals(1, result.size());
    NotificationSummary summary = result.get(0);
    assertEquals(id, summary.id());
    assertEquals(Channel.EMAIL, summary.channel());
    assertEquals("user@example.com", summary.recipient());
    assertEquals("welcome", summary.templateName());
    assertEquals(NotificationStatus.PENDING, summary.status());
    assertEquals(Priority.HIGH, summary.priority());
    assertEquals(now, summary.createdAt());
  }

  @Test
  void getByStatus_noResults_returnsEmptyList() {
    when(notificationRepository.findByStatus(NotificationStatus.FAILED))
        .thenReturn(List.of());

    List<NotificationSummary> result = service.getByStatus(NotificationStatus.FAILED);

    assertTrue(result.isEmpty());
  }

  @Test
  void getByStatus_passesCorrectStatusToRepository() {
    when(notificationRepository.findByStatus(NotificationStatus.SENT))
        .thenReturn(List.of());

    service.getByStatus(NotificationStatus.SENT);

    verify(notificationRepository).findByStatus(NotificationStatus.SENT);
  }
}
