package com.aegisnotify.audit.infrastructure.persistence.mongo;

import com.aegisnotify.audit.domain.enums.AuditStatus;
import com.aegisnotify.audit.domain.enums.Channel;
import com.aegisnotify.audit.domain.enums.Priority;
import com.aegisnotify.audit.domain.model.AuditEvent;
import com.aegisnotify.audit.domain.model.AuditTrail;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Maps between domain models and MongoDB documents.
 */
@Component
public class AuditPersistenceMapper {

  /**
   * Converts a domain AuditEvent to an embedded AuditEventDocument.
   */
  public AuditEventDocument toEventDocument(AuditEvent event) {
    return new AuditEventDocument(
        event.getId(),
        event.getStatus().name(),
        event.getDetails(),
        event.getChannel().name(),
        event.getRecipient(),
        event.getPriority().name(),
        event.getCreatedAt()
    );
  }

  /**
   * Converts an AuditTrailDocument to the domain AuditTrail.
   */
  public AuditTrail toDomain(AuditTrailDocument doc) {
    UUID notificationId = UUID.fromString(doc.id());
    List<AuditEvent> events = doc.events().stream()
        .map(eventDoc -> toEvent(eventDoc, notificationId))
        .toList();

    return AuditTrail.reconstitute(
        notificationId,
        AuditStatus.valueOf(doc.currentStatus()),
        events,
        doc.createdAt(),
        doc.updatedAt()
    );
  }

  private AuditEvent toEvent(AuditEventDocument doc,
      UUID notificationId) {
    return AuditEvent.reconstitute(
        doc.id(),
        notificationId,
        AuditStatus.valueOf(doc.status()),
        doc.details(),
        Channel.valueOf(doc.channel()),
        doc.recipient(),
        Priority.valueOf(doc.priority()),
        doc.createdAt()
    );
  }
}
