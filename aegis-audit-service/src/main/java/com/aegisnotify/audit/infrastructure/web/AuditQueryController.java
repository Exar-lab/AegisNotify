package com.aegisnotify.audit.infrastructure.web;

import com.aegisnotify.audit.application.dto.AuditEventSummary;
import com.aegisnotify.audit.application.dto.AuditSearchQuery;
import com.aegisnotify.audit.application.dto.AuditTrailResponse;
import com.aegisnotify.audit.application.dto.PagedResponse;
import com.aegisnotify.audit.application.port.in.GetAuditTrailUseCase;
import com.aegisnotify.audit.application.port.in.SearchAuditEventsUseCase;
import com.aegisnotify.audit.domain.enums.AuditStatus;
import com.aegisnotify.audit.domain.enums.Channel;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for querying audit trails.
 *
 * <p>Exposes endpoints for retrieving a full audit trail by
 * notificationId and for searching audit events with filters.</p>
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditQueryController {

  private final GetAuditTrailUseCase getAuditTrailUseCase;
  private final SearchAuditEventsUseCase searchAuditEventsUseCase;

  public AuditQueryController(
      GetAuditTrailUseCase getAuditTrailUseCase,
      SearchAuditEventsUseCase searchAuditEventsUseCase) {
    this.getAuditTrailUseCase = getAuditTrailUseCase;
    this.searchAuditEventsUseCase = searchAuditEventsUseCase;
  }

  /**
   * Returns the full audit trail for a notification.
   *
   * @param notificationId the notification identifier
   * @return ordered audit trail with all events
   */
  @GetMapping("/{notificationId}")
  public ResponseEntity<AuditTrailResponse> getByNotificationId(
      @PathVariable UUID notificationId) {
    AuditTrailResponse response =
        getAuditTrailUseCase.getByNotificationId(notificationId);
    return ResponseEntity.ok(response);
  }

  /**
   * Searches audit events with optional filters.
   *
   * @param channel optional channel filter
   * @param status optional status filter
   * @param from optional start timestamp filter
   * @param to optional end timestamp filter
   * @param page page number (default 0)
   * @param size page size (default 20)
   * @return paginated audit event summaries
   */
  @GetMapping
  public ResponseEntity<PagedResponse<AuditEventSummary>> search(
      @RequestParam(required = false) Channel channel,
      @RequestParam(required = false) AuditStatus status,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    AuditSearchQuery query =
        new AuditSearchQuery(status, channel, from, to, page, size);
    PagedResponse<AuditEventSummary> response =
        searchAuditEventsUseCase.search(query);
    return ResponseEntity.ok(response);
  }
}
