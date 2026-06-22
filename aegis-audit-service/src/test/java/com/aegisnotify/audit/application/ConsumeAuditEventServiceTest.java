package com.aegisnotify.audit.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.audit.application.dto.AuditEventCommand;
import com.aegisnotify.audit.application.port.out.AuditEventRepository;
import com.aegisnotify.audit.application.port.out.EncryptionPort;
import com.aegisnotify.audit.application.service.ConsumeAuditEventService;
import com.aegisnotify.audit.domain.enums.AuditStatus;
import com.aegisnotify.audit.domain.enums.Channel;
import com.aegisnotify.audit.domain.enums.Priority;
import com.aegisnotify.audit.domain.model.AuditEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsumeAuditEventServiceTest {

  @Mock
  private AuditEventRepository auditEventRepository;

  @Mock
  private EncryptionPort encryptionPort;

  @InjectMocks
  private ConsumeAuditEventService service;

  @Test
  void consume_happyPath_encryptsRecipientAndCallsAppendToTrail() {
    UUID notificationId = UUID.randomUUID();
    AuditEventCommand command = new AuditEventCommand(
        notificationId, "SENT", "Delivered via SendGrid",
        "EMAIL", "user@example.com", "HIGH", Instant.now()
    );
    when(encryptionPort.encrypt("user@example.com"))
        .thenReturn("encrypted-value");

    service.consume(command);

    verify(encryptionPort).encrypt("user@example.com");
    verify(auditEventRepository).appendToTrail(argThat(event ->
        event.getNotificationId().equals(notificationId)
            && event.getStatus() == AuditStatus.SENT
            && event.getChannel() == Channel.EMAIL
            && event.getRecipient().equals("encrypted-value")
            && event.getPriority() == Priority.HIGH
            && event.getDetails().equals("Delivered via SendGrid")
    ));
  }

  @Test
  void consume_withSmsChannel_parsesEnumsCorrectly() {
    UUID notificationId = UUID.randomUUID();
    AuditEventCommand command = new AuditEventCommand(
        notificationId, "QUEUED", "Queued for delivery",
        "SMS", "+34600000000", "MEDIUM", Instant.now()
    );
    when(encryptionPort.encrypt("+34600000000"))
        .thenReturn("encrypted-phone");

    service.consume(command);

    verify(encryptionPort).encrypt("+34600000000");
    verify(auditEventRepository).appendToTrail(argThat(event ->
        event.getStatus() == AuditStatus.QUEUED
            && event.getChannel() == Channel.SMS
            && event.getPriority() == Priority.MEDIUM
            && event.getRecipient().equals("encrypted-phone")
    ));
  }
}
