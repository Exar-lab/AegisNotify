package com.aegisnotify.audit.application.port.out;

import com.aegisnotify.audit.application.dto.AuditSearchQuery;
import com.aegisnotify.audit.application.dto.PagedResponse;
import com.aegisnotify.audit.domain.model.AuditEvent;
import com.aegisnotify.audit.domain.model.AuditTrail;
import java.util.Optional;
import java.util.UUID;

public interface AuditEventRepository {

  void appendToTrail(AuditEvent event);

  Optional<AuditTrail> findByNotificationId(UUID notificationId);

  PagedResponse<AuditTrail> search(AuditSearchQuery query);
}
