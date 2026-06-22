package com.aegisnotify.audit.application.port.in;

import com.aegisnotify.audit.application.dto.AuditTrailResponse;
import java.util.UUID;

public interface GetAuditTrailUseCase {

  AuditTrailResponse getByNotificationId(UUID notificationId);
}
