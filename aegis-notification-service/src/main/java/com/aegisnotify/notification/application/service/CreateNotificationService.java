package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.dto.CreateNotificationCommand;
import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.port.in.CreateNotificationUseCase;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.exception.TemplateNotFoundException;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateNotificationService implements CreateNotificationUseCase {

  private final TemplateRepository templateRepository;
  private final NotificationRepository notificationRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final AuditEventPublisherPort auditEventPublisherPort;

  public CreateNotificationService(TemplateRepository templateRepository,
      NotificationRepository notificationRepository,
      OutboxEventRepository outboxEventRepository,
      NotificationLogRepository notificationLogRepository,
      AuditEventPublisherPort auditEventPublisherPort) {
    this.templateRepository = templateRepository;
    this.notificationRepository = notificationRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.notificationLogRepository = notificationLogRepository;
    this.auditEventPublisherPort = auditEventPublisherPort;
  }

  @Override
  @Transactional
  public NotificationResponse create(CreateNotificationCommand command) {
    templateRepository.findActiveByName(command.templateName())
        .orElseThrow(() -> new TemplateNotFoundException(command.templateName()));

    Notification notification = Notification.create(
        command.channel(),
        command.recipient(),
        command.templateName(),
        command.parameters(),
        command.priority()
    );

    Notification saved = notificationRepository.save(notification);

    notificationLogRepository.save(
        NotificationLog.create(saved.getId(), LogStatus.PENDING, "Notification accepted")
    );

    Map<String, Object> payload = new HashMap<>();
    payload.put("id", saved.getId().toString());
    payload.put("channel", saved.getChannel().name());
    payload.put("recipient", saved.getRecipient());
    payload.put("templateName", saved.getTemplateName());
    payload.put("parameters", saved.getParameters());
    payload.put("priority", saved.getPriority().name());

    outboxEventRepository.save(OutboxEvent.create(saved.getId(), payload));

    auditEventPublisherPort.publish(new AuditEventMessage(
        saved.getId(),
        AuditStatusMapper.toAuditStatus(saved.getStatus()),
        "Notification created",
        saved.getChannel().name(),
        saved.getRecipient(),
        saved.getPriority().name(),
        Instant.now()
    ));

    return new NotificationResponse(saved.getId(), saved.getStatus());
  }
}
