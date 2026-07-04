package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.port.in.ProcessNotificationUseCase;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.TemplateRenderer;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.exception.NotificationNotFoundException;
import com.aegisnotify.notification.domain.exception.TemplateNotFoundException;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.domain.model.Template;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessNotificationService implements ProcessNotificationUseCase {

  private final NotificationRepository notificationRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final TemplateRepository templateRepository;
  private final TemplateRenderer templateRenderer;
  private final NotificationProviderPort notificationProviderPort;
  private final AuditEventPublisherPort auditEventPublisherPort;

  public ProcessNotificationService(NotificationRepository notificationRepository,
      NotificationLogRepository notificationLogRepository,
      TemplateRepository templateRepository,
      TemplateRenderer templateRenderer,
      NotificationProviderPort notificationProviderPort,
      AuditEventPublisherPort auditEventPublisherPort) {
    this.notificationRepository = notificationRepository;
    this.notificationLogRepository = notificationLogRepository;
    this.templateRepository = templateRepository;
    this.templateRenderer = templateRenderer;
    this.notificationProviderPort = notificationProviderPort;
    this.auditEventPublisherPort = auditEventPublisherPort;
  }

  @Override
  @Transactional
  public NotificationResponse process(UUID notificationId) {
    Notification notification = notificationRepository.findById(notificationId)
        .orElseThrow(() -> new NotificationNotFoundException(notificationId));

    Notification processing = notification.markProcessing();
    notificationRepository.save(processing);
    notificationLogRepository.save(
        NotificationLog.create(notificationId, LogStatus.PROCESSING, "Processing started")
    );

    publishAuditEvent(processing, AuditStatusMapper.toAuditStatus(processing.getStatus()),
        "Processing started");

    Template template = templateRepository.findActiveByName(processing.getTemplateName())
        .orElseThrow(() -> new TemplateNotFoundException(processing.getTemplateName()));

    String renderedBody = templateRenderer.render(template.getBody(), processing.getParameters());

    ProviderResult result = notificationProviderPort.send(
        processing.getChannel(), processing.getRecipient(),
        renderedBody, template.getSubject()
    );

    Notification updated = applyProviderResult(processing, result);
    notificationRepository.save(updated);
    notificationLogRepository.save(
        NotificationLog.create(notificationId, toLogStatus(result.outcome()),
            buildLogDetail(result))
    );

    publishAuditEvent(updated, toAuditStatusFromResult(result), buildLogDetail(result));

    return new NotificationResponse(updated.getId(), updated.getStatus());
  }

  private Notification applyProviderResult(Notification notification, ProviderResult result) {
    return switch (result.outcome()) {
      case SENT -> notification.markSent(result.providerName());
      case SENT_VIA_FALLBACK -> notification.markSentViaFallback(result.providerName());
      case FAILED -> notification.markFailed(result.errorDetail());
      case FAILED_CRITICAL -> notification.markFailedCritical(result.errorDetail());
    };
  }

  private LogStatus toLogStatus(ProviderResult.Outcome outcome) {
    return switch (outcome) {
      case SENT -> LogStatus.SENT;
      case SENT_VIA_FALLBACK -> LogStatus.SENT_VIA_FALLBACK;
      case FAILED -> LogStatus.FAILED;
      case FAILED_CRITICAL -> LogStatus.FAILED_CRITICAL;
    };
  }

  private String buildLogDetail(ProviderResult result) {
    return switch (result.outcome()) {
      case SENT -> "Sent via " + result.providerName();
      case SENT_VIA_FALLBACK -> "Sent via fallback provider " + result.providerName();
      case FAILED -> "Delivery failed: " + result.errorDetail();
      case FAILED_CRITICAL -> "Critical failure, sent to DLQ: " + result.errorDetail();
    };
  }

  private String toAuditStatusFromResult(ProviderResult result) {
    return switch (result.outcome()) {
      case SENT -> AuditStatusMapper.toAuditStatus(
          com.aegisnotify.notification.domain.enums.NotificationStatus.SENT);
      case SENT_VIA_FALLBACK -> AuditStatusMapper.toAuditStatus(
          com.aegisnotify.notification.domain.enums.NotificationStatus.SENT_VIA_FALLBACK);
      case FAILED -> AuditStatusMapper.toProviderFailStatus(true);
      case FAILED_CRITICAL -> AuditStatusMapper.toAuditStatus(
          com.aegisnotify.notification.domain.enums.NotificationStatus.FAILED_CRITICAL);
    };
  }

  private void publishAuditEvent(Notification notification, String status, String details) {
    auditEventPublisherPort.publish(new AuditEventMessage(
        notification.getId(),
        status,
        details,
        notification.getChannel().name(),
        notification.getRecipient(),
        notification.getPriority().name(),
        Instant.now()
    ));
  }
}
