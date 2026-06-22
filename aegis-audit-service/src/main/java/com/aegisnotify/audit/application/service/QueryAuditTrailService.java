package com.aegisnotify.audit.application.service;

import com.aegisnotify.audit.application.dto.AuditEventEntry;
import com.aegisnotify.audit.application.dto.AuditEventSummary;
import com.aegisnotify.audit.application.dto.AuditSearchQuery;
import com.aegisnotify.audit.application.dto.AuditTrailResponse;
import com.aegisnotify.audit.application.dto.PagedResponse;
import com.aegisnotify.audit.application.port.in.GetAuditTrailUseCase;
import com.aegisnotify.audit.application.port.in.SearchAuditEventsUseCase;
import com.aegisnotify.audit.application.port.out.AuditEventRepository;
import com.aegisnotify.audit.application.port.out.EncryptionPort;
import com.aegisnotify.audit.domain.exception.AuditTrailNotFoundException;
import com.aegisnotify.audit.domain.model.AuditTrail;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QueryAuditTrailService
    implements GetAuditTrailUseCase, SearchAuditEventsUseCase {

  private final AuditEventRepository auditEventRepository;
  private final EncryptionPort encryptionPort;

  public QueryAuditTrailService(AuditEventRepository auditEventRepository,
      EncryptionPort encryptionPort) {
    this.auditEventRepository = auditEventRepository;
    this.encryptionPort = encryptionPort;
  }

  @Override
  public AuditTrailResponse getByNotificationId(UUID notificationId) {
    AuditTrail trail = auditEventRepository.findByNotificationId(notificationId)
        .orElseThrow(() -> new AuditTrailNotFoundException(notificationId));

    List<AuditEventEntry> entries = trail.getEvents().stream()
        .map(event -> new AuditEventEntry(
            event.getStatus().name(),
            event.getDetails(),
            event.getCreatedAt()
        ))
        .toList();

    return new AuditTrailResponse(
        trail.getNotificationId(),
        trail.getCurrentStatus().name(),
        entries,
        trail.getCreatedAt(),
        trail.getUpdatedAt()
    );
  }

  @Override
  public PagedResponse<AuditEventSummary> search(AuditSearchQuery query) {
    PagedResponse<AuditTrail> result = auditEventRepository.search(query);

    List<AuditEventSummary> summaries = result.content().stream()
        .map(trail -> new AuditEventSummary(
            trail.getNotificationId(),
            trail.getCurrentStatus().name(),
            trail.getEvents().isEmpty() ? null
                : trail.getEvents().get(0).getChannel().name(),
            trail.getEvents().isEmpty() ? null
                : trail.getEvents().get(0).getPriority().name(),
            trail.getCreatedAt()
        ))
        .toList();

    return new PagedResponse<>(
        summaries,
        result.page(),
        result.size(),
        result.totalElements(),
        result.totalPages()
    );
  }
}
