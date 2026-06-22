package com.aegisnotify.audit.application.service;

import com.aegisnotify.audit.application.dto.AuditEventCommand;
import com.aegisnotify.audit.application.port.in.ConsumeAuditEventUseCase;
import com.aegisnotify.audit.application.port.out.AuditEventRepository;
import com.aegisnotify.audit.application.port.out.EncryptionPort;
import com.aegisnotify.audit.domain.enums.AuditStatus;
import com.aegisnotify.audit.domain.enums.Channel;
import com.aegisnotify.audit.domain.enums.Priority;
import com.aegisnotify.audit.domain.model.AuditEvent;
import org.springframework.stereotype.Service;

@Service
public class ConsumeAuditEventService implements ConsumeAuditEventUseCase {

  private final AuditEventRepository auditEventRepository;
  private final EncryptionPort encryptionPort;

  public ConsumeAuditEventService(AuditEventRepository auditEventRepository,
      EncryptionPort encryptionPort) {
    this.auditEventRepository = auditEventRepository;
    this.encryptionPort = encryptionPort;
  }

  @Override
  public void consume(AuditEventCommand command) {
    AuditStatus status = AuditStatus.valueOf(command.status());
    Channel channel = Channel.valueOf(command.channel());
    Priority priority = Priority.valueOf(command.priority());
    String encryptedRecipient = encryptionPort.encrypt(command.recipient());

    AuditEvent event = AuditEvent.create(
        command.notificationId(), status, command.details(),
        channel, encryptedRecipient, priority
    );

    auditEventRepository.appendToTrail(event);
  }
}
