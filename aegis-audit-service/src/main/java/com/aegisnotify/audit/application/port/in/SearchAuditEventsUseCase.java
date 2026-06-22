package com.aegisnotify.audit.application.port.in;

import com.aegisnotify.audit.application.dto.AuditEventSummary;
import com.aegisnotify.audit.application.dto.AuditSearchQuery;
import com.aegisnotify.audit.application.dto.PagedResponse;

public interface SearchAuditEventsUseCase {

  PagedResponse<AuditEventSummary> search(AuditSearchQuery query);
}
