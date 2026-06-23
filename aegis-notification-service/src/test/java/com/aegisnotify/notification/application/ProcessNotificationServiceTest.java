package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.TemplateRenderer;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.application.service.ProcessNotificationService;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.exception.NotificationNotFoundException;
import com.aegisnotify.notification.domain.exception.TemplateNotFoundException;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.domain.model.Template;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessNotificationServiceTest {

  @Mock
  private NotificationRepository notificationRepository;

  @Mock
  private NotificationLogRepository notificationLogRepository;

  @Mock
  private TemplateRepository templateRepository;

  @Mock
  private TemplateRenderer templateRenderer;

  @Mock
  private NotificationProviderPort notificationProviderPort;

  @Mock
  private AuditEventPublisherPort auditEventPublisherPort;

  @InjectMocks
  private ProcessNotificationService service;

  @Test
  void process_sentSuccessfully_returnsSentStatus() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );
    Template template = Template.reconstitute(
        UUID.randomUUID(), "welcome", Channel.EMAIL,
        "Welcome", "Hello {{name}}", List.of("name"),
        true, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(templateRepository.findActiveByName("welcome"))
        .thenReturn(Optional.of(template));
    when(templateRenderer.render(anyString(), anyMap()))
        .thenReturn("Hello John");
    when(notificationProviderPort.send(any(), anyString(), anyString(), anyString()))
        .thenReturn(new ProviderResult(ProviderResult.Outcome.SENT, "SendGrid", null));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NotificationResponse response = service.process(notificationId);

    assertNotNull(response.id());
    assertEquals(NotificationStatus.SENT, response.status());
    verify(notificationRepository).findById(notificationId);
    verify(templateRenderer).render("Hello {{name}}", Map.of("name", "John"));
    verify(notificationProviderPort).send(
        Channel.EMAIL, "user@example.com", "Hello John", "Welcome"
    );
  }

  @Test
  void process_sentViaFallback_returnsFallbackStatus() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.SMS, "+34600000000", "otp",
        Map.of("code", "1234"), Priority.HIGH, NotificationStatus.QUEUED,
        null, null, Instant.now(), Instant.now()
    );
    Template template = Template.reconstitute(
        UUID.randomUUID(), "otp", Channel.SMS,
        null, "Your code: {{code}}", List.of("code"),
        true, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(templateRepository.findActiveByName("otp"))
        .thenReturn(Optional.of(template));
    when(templateRenderer.render(anyString(), anyMap()))
        .thenReturn("Your code: 1234");
    when(notificationProviderPort.send(any(), anyString(), anyString(), any()))
        .thenReturn(new ProviderResult(
            ProviderResult.Outcome.SENT_VIA_FALLBACK, "TwilioBackup", null));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NotificationResponse response = service.process(notificationId);

    assertEquals(NotificationStatus.SENT_VIA_FALLBACK, response.status());
  }

  @Test
  void process_failedCritical_returnsFailedCriticalStatus() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.LOW, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );
    Template template = Template.reconstitute(
        UUID.randomUUID(), "welcome", Channel.EMAIL,
        "Welcome", "Hello", List.of(),
        true, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(templateRepository.findActiveByName("welcome"))
        .thenReturn(Optional.of(template));
    when(templateRenderer.render(anyString(), anyMap()))
        .thenReturn("Hello");
    when(notificationProviderPort.send(any(), anyString(), anyString(), anyString()))
        .thenReturn(new ProviderResult(
            ProviderResult.Outcome.FAILED_CRITICAL, null, "All providers exhausted"));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NotificationResponse response = service.process(notificationId);

    assertEquals(NotificationStatus.FAILED_CRITICAL, response.status());
  }

  @Test
  void process_notificationNotFound_throwsNotificationNotFoundException() {
    UUID notificationId = UUID.randomUUID();
    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.empty());

    assertThrows(NotificationNotFoundException.class,
        () -> service.process(notificationId));
    verify(notificationProviderPort, never()).send(any(), anyString(), anyString(), any());
  }

  @Test
  void process_templateNotFound_throwsTemplateNotFoundException() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "missing",
        Map.of(), Priority.HIGH, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(templateRepository.findActiveByName("missing"))
        .thenReturn(Optional.empty());

    assertThrows(TemplateNotFoundException.class,
        () -> service.process(notificationId));
    verify(notificationProviderPort, never()).send(any(), anyString(), anyString(), any());
  }

  @Test
  void process_sentSuccessfully_publishesProcessingAndSentAuditEvents() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );
    Template template = Template.reconstitute(
        UUID.randomUUID(), "welcome", Channel.EMAIL,
        "Welcome", "Hello {{name}}", List.of("name"),
        true, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(templateRepository.findActiveByName("welcome"))
        .thenReturn(Optional.of(template));
    when(templateRenderer.render(anyString(), anyMap()))
        .thenReturn("Hello John");
    when(notificationProviderPort.send(any(), anyString(), anyString(), anyString()))
        .thenReturn(new ProviderResult(ProviderResult.Outcome.SENT, "SendGrid", null));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.process(notificationId);

    ArgumentCaptor<AuditEventMessage> captor =
        ArgumentCaptor.forClass(AuditEventMessage.class);
    verify(auditEventPublisherPort, org.mockito.Mockito.atLeast(2)).publish(captor.capture());

    java.util.List<AuditEventMessage> events = captor.getAllValues();
    assertEquals("PROCESSING", events.get(0).status());
    assertEquals("SENT", events.get(1).status());
    assertEquals("EMAIL", events.get(1).channel());
    assertEquals("user@example.com", events.get(1).recipient());
  }

  @Test
  void process_failedOutcome_publishesProviderFailAuditStatus() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.SMS, "+34600000000", "otp",
        Map.of("code", "1234"), Priority.MEDIUM, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );
    Template template = Template.reconstitute(
        UUID.randomUUID(), "otp", Channel.SMS,
        null, "Code: {{code}}", List.of("code"),
        true, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(templateRepository.findActiveByName("otp"))
        .thenReturn(Optional.of(template));
    when(templateRenderer.render(anyString(), anyMap()))
        .thenReturn("Code: 1234");
    when(notificationProviderPort.send(any(), anyString(), anyString(), any()))
        .thenReturn(new ProviderResult(
            ProviderResult.Outcome.FAILED, "TwilioPrimary", "Connection timeout"));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.process(notificationId);

    ArgumentCaptor<AuditEventMessage> captor =
        ArgumentCaptor.forClass(AuditEventMessage.class);
    verify(auditEventPublisherPort, org.mockito.Mockito.atLeast(2)).publish(captor.capture());

    java.util.List<AuditEventMessage> events = captor.getAllValues();
    assertEquals("PROCESSING", events.get(0).status());
    assertEquals("PROVIDER_A_FAIL", events.get(1).status());
  }

  @Test
  void process_failedCriticalOutcome_publishesFailedCriticalAuditStatus() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.LOW, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );
    Template template = Template.reconstitute(
        UUID.randomUUID(), "welcome", Channel.EMAIL,
        "Welcome", "Hello", List.of(),
        true, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(templateRepository.findActiveByName("welcome"))
        .thenReturn(Optional.of(template));
    when(templateRenderer.render(anyString(), anyMap()))
        .thenReturn("Hello");
    when(notificationProviderPort.send(any(), anyString(), anyString(), anyString()))
        .thenReturn(new ProviderResult(
            ProviderResult.Outcome.FAILED_CRITICAL, null, "All providers exhausted"));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.process(notificationId);

    ArgumentCaptor<AuditEventMessage> captor =
        ArgumentCaptor.forClass(AuditEventMessage.class);
    verify(auditEventPublisherPort, org.mockito.Mockito.atLeast(2)).publish(captor.capture());

    java.util.List<AuditEventMessage> events = captor.getAllValues();
    assertEquals("PROCESSING", events.get(0).status());
    assertEquals("FAILED_CRITICAL", events.get(1).status());
  }
}
