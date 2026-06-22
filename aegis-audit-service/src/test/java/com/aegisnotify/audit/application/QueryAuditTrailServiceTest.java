package com.aegisnotify.audit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.aegisnotify.audit.application.dto.AuditEventSummary;
import com.aegisnotify.audit.application.dto.AuditSearchQuery;
import com.aegisnotify.audit.application.dto.AuditTrailResponse;
import com.aegisnotify.audit.application.dto.PagedResponse;
import com.aegisnotify.audit.application.port.out.AuditEventRepository;
import com.aegisnotify.audit.application.port.out.EncryptionPort;
import com.aegisnotify.audit.application.service.QueryAuditTrailService;
import com.aegisnotify.audit.domain.enums.AuditStatus;
import com.aegisnotify.audit.domain.enums.Channel;
import com.aegisnotify.audit.domain.enums.Priority;
import com.aegisnotify.audit.domain.exception.AuditTrailNotFoundException;
import com.aegisnotify.audit.domain.model.AuditEvent;
import com.aegisnotify.audit.domain.model.AuditTrail;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryAuditTrailServiceTest {

  @Mock
  private AuditEventRepository auditEventRepository;

  @Mock
  private EncryptionPort encryptionPort;

  @InjectMocks
  private QueryAuditTrailService service;

  @Test
  void getByNotificationId_found_returnsMappedResponse() {
    UUID notificationId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-06-01T10:00:00Z");
    Instant updatedAt = Instant.parse("2026-06-01T10:05:00Z");
    AuditEvent event = AuditEvent.reconstitute(
        UUID.randomUUID(), notificationId, AuditStatus.SENT,
        "Delivered", Channel.EMAIL, "encrypted-value",
        Priority.HIGH, createdAt
    );
    AuditTrail trail = AuditTrail.reconstitute(
        notificationId, AuditStatus.SENT, List.of(event),
        createdAt, updatedAt
    );
    when(auditEventRepository.findByNotificationId(notificationId))
        .thenReturn(Optional.of(trail));

    AuditTrailResponse response = service.getByNotificationId(notificationId);

    assertEquals(notificationId, response.notificationId());
    assertEquals("SENT", response.currentStatus());
    assertEquals(1, response.events().size());
    assertEquals("SENT", response.events().get(0).status());
    assertEquals("Delivered", response.events().get(0).details());
    assertEquals(createdAt, response.events().get(0).createdAt());
    assertEquals(createdAt, response.createdAt());
    assertEquals(updatedAt, response.updatedAt());
  }

  @Test
  void getByNotificationId_notFound_throwsAuditTrailNotFoundException() {
    UUID notificationId = UUID.randomUUID();
    when(auditEventRepository.findByNotificationId(notificationId))
        .thenReturn(Optional.empty());

    assertThrows(AuditTrailNotFoundException.class,
        () -> service.getByNotificationId(notificationId));
  }

  @Test
  void search_delegatesToRepositoryAndMapsPaginatedResults() {
    AuditSearchQuery query = new AuditSearchQuery(
        AuditStatus.SENT, Channel.EMAIL, null, null, 0, 20
    );
    Instant createdAt = Instant.parse("2026-06-01T10:00:00Z");
    Instant updatedAt = Instant.parse("2026-06-01T10:05:00Z");
    UUID notificationId = UUID.randomUUID();
    AuditEvent event = AuditEvent.reconstitute(
        UUID.randomUUID(), notificationId, AuditStatus.SENT,
        "Delivered", Channel.EMAIL, "encrypted",
        Priority.HIGH, createdAt
    );
    AuditTrail trail = AuditTrail.reconstitute(
        notificationId, AuditStatus.SENT, List.of(event),
        createdAt, updatedAt
    );
    PagedResponse<AuditTrail> repoResult = new PagedResponse<>(
        List.of(trail), 0, 20, 1L, 1
    );
    when(auditEventRepository.search(query)).thenReturn(repoResult);

    PagedResponse<AuditEventSummary> response = service.search(query);

    assertEquals(1, response.content().size());
    assertEquals(notificationId, response.content().get(0).notificationId());
    assertEquals("SENT", response.content().get(0).currentStatus());
    assertEquals("EMAIL", response.content().get(0).channel());
    assertEquals("HIGH", response.content().get(0).priority());
    assertEquals(0, response.page());
    assertEquals(20, response.size());
    assertEquals(1L, response.totalElements());
    assertEquals(1, response.totalPages());
  }
}
