package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.CreateNotificationCommand;
import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.application.service.CreateNotificationService;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.exception.InvalidRecipientException;
import com.aegisnotify.notification.domain.exception.TemplateNotFoundException;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import com.aegisnotify.notification.domain.model.Template;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateNotificationServiceTest {

  @Mock
  private TemplateRepository templateRepository;

  @Mock
  private NotificationRepository notificationRepository;

  @Mock
  private OutboxEventRepository outboxEventRepository;

  @Mock
  private NotificationLogRepository notificationLogRepository;

  @InjectMocks
  private CreateNotificationService service;

  @Test
  void create_happyPath_savesNotificationOutboxAndLog() {
    Template template = Template.reconstitute(
        UUID.randomUUID(), "welcome", Channel.EMAIL,
        "Welcome", "Hello {{name}}", List.of("name"),
        true, Instant.now(), Instant.now()
    );
    when(templateRepository.findActiveByName("welcome"))
        .thenReturn(Optional.of(template));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CreateNotificationCommand command = new CreateNotificationCommand(
        Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH
    );

    NotificationResponse response = service.create(command);

    assertNotNull(response.id());
    assertEquals(NotificationStatus.PENDING, response.status());
    verify(templateRepository).findActiveByName("welcome");
    verify(notificationRepository).save(any(Notification.class));
    verify(outboxEventRepository).save(any(OutboxEvent.class));
    verify(notificationLogRepository).save(any(NotificationLog.class));
  }

  @Test
  void create_templateNotFound_throwsTemplateNotFoundException() {
    when(templateRepository.findActiveByName("missing"))
        .thenReturn(Optional.empty());

    CreateNotificationCommand command = new CreateNotificationCommand(
        Channel.EMAIL, "user@example.com", "missing",
        Map.of(), Priority.HIGH
    );

    assertThrows(TemplateNotFoundException.class, () -> service.create(command));
    verify(notificationRepository, never()).save(any(Notification.class));
  }

  @Test
  void create_invalidRecipient_throwsInvalidRecipientException() {
    Template template = Template.reconstitute(
        UUID.randomUUID(), "welcome", Channel.EMAIL,
        "Welcome", "Hello", List.of(),
        true, Instant.now(), Instant.now()
    );
    when(templateRepository.findActiveByName("welcome"))
        .thenReturn(Optional.of(template));

    CreateNotificationCommand command = new CreateNotificationCommand(
        Channel.EMAIL, "bad-email", "welcome",
        Map.of(), Priority.HIGH
    );

    assertThrows(InvalidRecipientException.class, () -> service.create(command));
    verify(notificationRepository, never()).save(any(Notification.class));
  }
}
