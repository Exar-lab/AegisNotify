package com.aegisnotify.audit.infrastructure.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.audit.application.dto.AuditSearchQuery;
import com.aegisnotify.audit.application.dto.PagedResponse;
import com.aegisnotify.audit.domain.enums.AuditStatus;
import com.aegisnotify.audit.domain.enums.Channel;
import com.aegisnotify.audit.domain.enums.Priority;
import com.aegisnotify.audit.domain.model.AuditEvent;
import com.aegisnotify.audit.domain.model.AuditTrail;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class MongoAuditEventRepositoryAdapterTest {

  @Mock
  private SpringDataAuditTrailRepository springDataRepository;

  @Mock
  private MongoOperations mongoTemplate;

  @Mock
  private AuditPersistenceMapper mapper;

  @InjectMocks
  private MongoAuditEventRepositoryAdapter adapter;

  @Test
  void appendToTrail_newTrail_upsertsDocumentWithPushToEventsArray() {
    UUID notificationId = UUID.randomUUID();
    AuditEvent event = AuditEvent.create(
        notificationId, AuditStatus.PENDING, "Notification created",
        Channel.EMAIL, "encrypted-recipient", Priority.HIGH
    );

    AuditEventDocument eventDoc = new AuditEventDocument(
        event.getId(), event.getStatus().name(), event.getDetails(),
        event.getChannel().name(), event.getRecipient(),
        event.getPriority().name(), event.getCreatedAt()
    );
    when(mapper.toEventDocument(event)).thenReturn(eventDoc);

    adapter.appendToTrail(event);

    verify(mongoTemplate).upsert(
        any(Query.class), any(Update.class),
        eq(AuditTrailDocument.class)
    );
    verify(mapper).toEventDocument(event);
  }

  @Test
  void findByNotificationId_existingTrail_returnsMappedDomain() {
    UUID notificationId = UUID.randomUUID();
    Instant now = Instant.now();
    AuditEventDocument eventDoc = new AuditEventDocument(
        UUID.randomUUID(), "SENT", "Delivered",
        "EMAIL", "encrypted", "HIGH", now
    );
    AuditTrailDocument doc = new AuditTrailDocument(
        notificationId.toString(), "SENT", List.of(eventDoc), now, now
    );
    when(springDataRepository.findById(notificationId.toString()))
        .thenReturn(Optional.of(doc));

    AuditEvent domainEvent = AuditEvent.reconstitute(
        eventDoc.id(), notificationId, AuditStatus.SENT, "Delivered",
        Channel.EMAIL, "encrypted", Priority.HIGH, now
    );
    AuditTrail domainTrail = AuditTrail.reconstitute(
        notificationId, AuditStatus.SENT, List.of(domainEvent), now, now
    );
    when(mapper.toDomain(doc)).thenReturn(domainTrail);

    Optional<AuditTrail> result =
        adapter.findByNotificationId(notificationId);

    assertTrue(result.isPresent());
    assertEquals(notificationId, result.get().getNotificationId());
    assertEquals(AuditStatus.SENT, result.get().getCurrentStatus());
    assertEquals(1, result.get().getEvents().size());
  }

  @Test
  void findByNotificationId_notFound_returnsEmpty() {
    UUID notificationId = UUID.randomUUID();
    when(springDataRepository.findById(notificationId.toString()))
        .thenReturn(Optional.empty());

    Optional<AuditTrail> result =
        adapter.findByNotificationId(notificationId);

    assertTrue(result.isEmpty());
  }

  @Test
  void search_withChannelFilter_returnsPagedResults() {
    AuditSearchQuery query = new AuditSearchQuery(
        null, Channel.EMAIL, null, null, 0, 20
    );

    Instant now = Instant.now();
    UUID notificationId = UUID.randomUUID();
    AuditEventDocument eventDoc = new AuditEventDocument(
        UUID.randomUUID(), "SENT", "Delivered",
        "EMAIL", "encrypted", "HIGH", now
    );
    AuditTrailDocument doc = new AuditTrailDocument(
        notificationId.toString(), "SENT", List.of(eventDoc), now, now
    );

    AuditEvent domainEvent = AuditEvent.reconstitute(
        eventDoc.id(), notificationId, AuditStatus.SENT, "Delivered",
        Channel.EMAIL, "encrypted", Priority.HIGH, now
    );
    AuditTrail trail = AuditTrail.reconstitute(
        notificationId, AuditStatus.SENT, List.of(domainEvent), now, now
    );
    when(mapper.toDomain(doc)).thenReturn(trail);

    // MongoOperations returns docs and count
    when(mongoTemplate.find(any(Query.class),
        eq(AuditTrailDocument.class)))
        .thenReturn(List.of(doc));
    when(mongoTemplate.count(any(Query.class),
        eq(AuditTrailDocument.class)))
        .thenReturn(1L);

    PagedResponse<AuditTrail> result = adapter.search(query);

    assertEquals(1, result.content().size());
    assertEquals(notificationId,
        result.content().get(0).getNotificationId());
    assertEquals(0, result.page());
    assertEquals(20, result.size());
    assertEquals(1L, result.totalElements());
  }
}
