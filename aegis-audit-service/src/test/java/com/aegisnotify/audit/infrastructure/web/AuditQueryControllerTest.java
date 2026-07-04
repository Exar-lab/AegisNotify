package com.aegisnotify.audit.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegisnotify.audit.application.dto.AuditEventEntry;
import com.aegisnotify.audit.application.dto.AuditEventSummary;
import com.aegisnotify.audit.application.dto.AuditSearchQuery;
import com.aegisnotify.audit.application.dto.AuditTrailResponse;
import com.aegisnotify.audit.application.dto.PagedResponse;
import com.aegisnotify.audit.application.port.in.GetAuditTrailUseCase;
import com.aegisnotify.audit.application.port.in.SearchAuditEventsUseCase;
import com.aegisnotify.audit.domain.exception.AuditTrailNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuditQueryController.class)
class AuditQueryControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private GetAuditTrailUseCase getAuditTrailUseCase;

  @MockitoBean
  private SearchAuditEventsUseCase searchAuditEventsUseCase;

  @Test
  void getByNotificationId_found_returnsOrderedTrail() throws Exception {
    UUID notificationId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-06-01T10:00:00Z");
    Instant updatedAt = Instant.parse("2026-06-01T10:05:00Z");

    AuditTrailResponse response = new AuditTrailResponse(
        notificationId, "SENT",
        List.of(
            new AuditEventEntry("PENDING", "Created", createdAt),
            new AuditEventEntry("SENT", "Delivered", updatedAt)
        ),
        createdAt, updatedAt
    );

    when(getAuditTrailUseCase.getByNotificationId(notificationId))
        .thenReturn(response);

    mockMvc.perform(
            get("/api/v1/audit/{notificationId}", notificationId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notificationId")
            .value(notificationId.toString()))
        .andExpect(jsonPath("$.currentStatus").value("SENT"))
        .andExpect(jsonPath("$.events.length()").value(2))
        .andExpect(jsonPath("$.events[0].status").value("PENDING"))
        .andExpect(jsonPath("$.events[1].status").value("SENT"));
  }

  @Test
  void getByNotificationId_notFound_returns404() throws Exception {
    UUID notificationId = UUID.randomUUID();
    when(getAuditTrailUseCase.getByNotificationId(notificationId))
        .thenThrow(new AuditTrailNotFoundException(notificationId));

    mockMvc.perform(
            get("/api/v1/audit/{notificationId}", notificationId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void search_withChannelFilter_returnsFilteredResults() throws Exception {
    UUID notificationId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

    PagedResponse<AuditEventSummary> pagedResponse = new PagedResponse<>(
        List.of(new AuditEventSummary(
            notificationId, "SENT", "EMAIL", "HIGH", createdAt
        )),
        0, 20, 1L, 1
    );

    when(searchAuditEventsUseCase.search(any(AuditSearchQuery.class)))
        .thenReturn(pagedResponse);

    mockMvc.perform(get("/api/v1/audit")
            .param("channel", "EMAIL")
            .param("from", "2026-01-01T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].channel").value("EMAIL"))
        .andExpect(jsonPath("$.content[0].notificationId")
            .value(notificationId.toString()))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void search_noResults_returnsEmptyPage() throws Exception {
    PagedResponse<AuditEventSummary> emptyResponse = new PagedResponse<>(
        List.of(), 0, 20, 0L, 0
    );

    when(searchAuditEventsUseCase.search(any(AuditSearchQuery.class)))
        .thenReturn(emptyResponse);

    mockMvc.perform(get("/api/v1/audit"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.totalElements").value(0));
  }
}
