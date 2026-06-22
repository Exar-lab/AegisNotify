package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.NotificationSummary;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.service.GetNotificationsByChannelService;
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
class GetNotificationsByChannelServiceTest {

  @Mock
  private NotificationRepository notificationRepository;

  @InjectMocks
  private GetNotificationsByChannelService service;

  @Test
  void getByChannel_found_returnsMappedSummaries() {
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();

    Notification notification = Notification.reconstitute(
        id, Channel.SMS, "+34600000000", "verification",
        Map.of("code", "1234"), Priority.MEDIUM, NotificationStatus.SENT,
        null, null, now, now
    );
    when(notificationRepository.findByChannel(Channel.SMS))
        .thenReturn(List.of(notification));

    List<NotificationSummary> result = service.getByChannel(Channel.SMS);

    assertEquals(1, result.size());
    NotificationSummary summary = result.get(0);
    assertEquals(id, summary.id());
    assertEquals(Channel.SMS, summary.channel());
    assertEquals("+34600000000", summary.recipient());
    assertEquals("verification", summary.templateName());
    assertEquals(NotificationStatus.SENT, summary.status());
    assertEquals(Priority.MEDIUM, summary.priority());
    assertEquals(now, summary.createdAt());
  }

  @Test
  void getByChannel_noResults_returnsEmptyList() {
    when(notificationRepository.findByChannel(Channel.PUSH))
        .thenReturn(List.of());

    List<NotificationSummary> result = service.getByChannel(Channel.PUSH);

    assertTrue(result.isEmpty());
  }

  @Test
  void getByChannel_passesCorrectChannelToRepository() {
    when(notificationRepository.findByChannel(Channel.WHATSAPP))
        .thenReturn(List.of());

    service.getByChannel(Channel.WHATSAPP);

    verify(notificationRepository).findByChannel(Channel.WHATSAPP);
  }
}
