package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.TemplateRenderer;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.application.service.NotificationProcessingTransactions;
import com.aegisnotify.notification.application.service.NotificationProcessingTransactions.PreparedNotification;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationProcessingTransactionsTest {

  @Mock
  private NotificationRepository notificationRepository;

  @Mock
  private NotificationLogRepository notificationLogRepository;

  @Mock
  private TemplateRepository templateRepository;

  @Mock
  private TemplateRenderer templateRenderer;

  @Mock
  private AuditEventPublisherPort auditEventPublisherPort;

  private NotificationProcessingTransactions transactions;

  @BeforeEach
  void setUp() {
    transactions = new NotificationProcessingTransactions(notificationRepository,
        notificationLogRepository, templateRepository, templateRenderer,
        auditEventPublisherPort);
  }

  @Test
  void prepare_returnsRenderedNotification() {
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
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PreparedNotification prepared = transactions.prepare(notificationId);

    assertEquals(NotificationStatus.PROCESSING, prepared.notification().getStatus());
    assertEquals("Welcome", prepared.subject());
    assertEquals("Hello John", prepared.renderedBody());

    ArgumentCaptor<AuditEventMessage> captor = ArgumentCaptor.forClass(AuditEventMessage.class);
    Mockito.verify(auditEventPublisherPort).publish(captor.capture());
    assertEquals("PROCESSING", captor.getValue().status());
  }

  @Test
  void prepare_notificationNotFound_throwsNotificationNotFoundException() {
    UUID notificationId = UUID.randomUUID();
    when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

    assertThrows(NotificationNotFoundException.class, () -> transactions.prepare(notificationId));
  }

  @Test
  void prepare_templateNotFound_throwsTemplateNotFoundException() {
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
    when(templateRepository.findActiveByName("missing")).thenReturn(Optional.empty());

    assertThrows(TemplateNotFoundException.class, () -> transactions.prepare(notificationId));
  }

  @Test
  void applyResult_sentOutcome_returnsSentStatusAndPublishesAuditEvent() {
    Notification processing = Notification.reconstitute(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now()
    );
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NotificationResponse response = transactions.applyResult(processing,
        new ProviderResult(ProviderResult.Outcome.SENT, "SendGrid", null));

    assertEquals(NotificationStatus.SENT, response.status());

    ArgumentCaptor<AuditEventMessage> captor = ArgumentCaptor.forClass(AuditEventMessage.class);
    Mockito.verify(auditEventPublisherPort).publish(captor.capture());
    assertEquals("SENT", captor.getValue().status());
  }

  @Test
  void applyResult_failedOutcome_publishesProviderFailAuditStatus() {
    Notification processing = Notification.reconstitute(
        UUID.randomUUID(), Channel.SMS, "+34600000000", "otp",
        Map.of("code", "1234"), Priority.MEDIUM, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now()
    );
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NotificationResponse response = transactions.applyResult(processing,
        new ProviderResult(ProviderResult.Outcome.FAILED, "TwilioPrimary", "Connection timeout"));

    assertEquals(NotificationStatus.FAILED, response.status());

    ArgumentCaptor<AuditEventMessage> captor = ArgumentCaptor.forClass(AuditEventMessage.class);
    Mockito.verify(auditEventPublisherPort).publish(captor.capture());
    assertEquals("PROVIDER_A_FAIL", captor.getValue().status());
  }

  @Test
  void applyResult_failedCriticalOutcome_returnsFailedCriticalStatus() {
    Notification processing = Notification.reconstitute(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.LOW, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now()
    );
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NotificationResponse response = transactions.applyResult(processing,
        new ProviderResult(ProviderResult.Outcome.FAILED_CRITICAL, null,
            "All providers exhausted"));

    assertEquals(NotificationStatus.FAILED_CRITICAL, response.status());
  }

  @Test
  void applyResult_sentViaFallbackOutcome_returnsFallbackStatus() {
    Notification processing = Notification.reconstitute(
        UUID.randomUUID(), Channel.SMS, "+34600000000", "otp",
        Map.of("code", "1234"), Priority.HIGH, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now()
    );
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NotificationResponse response = transactions.applyResult(processing,
        new ProviderResult(ProviderResult.Outcome.SENT_VIA_FALLBACK, "TwilioBackup", null));

    assertEquals(NotificationStatus.SENT_VIA_FALLBACK, response.status());
  }
}
