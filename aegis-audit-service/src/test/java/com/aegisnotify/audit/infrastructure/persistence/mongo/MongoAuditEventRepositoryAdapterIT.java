package com.aegisnotify.audit.infrastructure.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.audit.application.dto.AuditSearchQuery;
import com.aegisnotify.audit.application.dto.PagedResponse;
import com.aegisnotify.audit.domain.enums.AuditStatus;
import com.aegisnotify.audit.domain.enums.Channel;
import com.aegisnotify.audit.domain.enums.Priority;
import com.aegisnotify.audit.domain.model.AuditEvent;
import com.aegisnotify.audit.domain.model.AuditTrail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Exercises the real {@code $push} upsert and query semantics of
 * {@link MongoAuditEventRepositoryAdapter} against a MongoDB container.
 */
@DataMongoTest
@Import({MongoAuditEventRepositoryAdapter.class, AuditPersistenceMapper.class})
@Testcontainers
class MongoAuditEventRepositoryAdapterIT {

  @Container
  static final MongoDBContainer MONGO = new MongoDBContainer(
      DockerImageName.parse("mongo:7"))
      .withReuse(true);

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri",
        () -> MONGO.getReplicaSetUrl("audit-it"));
  }

  @Autowired
  private MongoAuditEventRepositoryAdapter adapter;

  @Autowired
  private MongoOperations mongoOperations;

  @BeforeEach
  void cleanCollection() {
    mongoOperations.dropCollection(AuditTrailDocument.class);
  }

  @Test
  void appendToTrail_twiceForSameNotification_accumulatesEventsInOneDocument() {
    UUID notificationId = UUID.randomUUID();
    AuditEvent first = AuditEvent.create(notificationId,
        AuditStatus.PENDING, "created", Channel.EMAIL, "enc-1",
        Priority.HIGH);
    AuditEvent second = AuditEvent.create(notificationId,
        AuditStatus.SENT, "delivered", Channel.EMAIL, "enc-1",
        Priority.HIGH);

    adapter.appendToTrail(first);
    adapter.appendToTrail(second);

    assertThat(mongoOperations.findAll(AuditTrailDocument.class))
        .hasSize(1);
    AuditTrail trail = adapter.findByNotificationId(notificationId)
        .orElseThrow();
    assertThat(trail.getEvents()).hasSize(2);
    assertThat(trail.getEvents())
        .extracting(AuditEvent::getId)
        .containsExactly(first.getId(), second.getId());
    assertThat(trail.getCurrentStatus()).isEqualTo(AuditStatus.SENT);
  }

  @Test
  void search_byStatus_returnsOnlyMatchingTrails() {
    UUID sent = seed(AuditStatus.SENT, Channel.EMAIL,
        Instant.parse("2025-01-10T00:00:00Z"));
    seed(AuditStatus.FAILED, Channel.EMAIL,
        Instant.parse("2025-01-10T00:00:00Z"));

    PagedResponse<AuditTrail> result = adapter.search(
        new AuditSearchQuery(AuditStatus.SENT, null, null, null, 0, 20));

    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.content())
        .extracting(AuditTrail::getNotificationId)
        .containsExactly(sent);
  }

  @Test
  void search_byChannel_returnsOnlyMatchingTrails() {
    seed(AuditStatus.SENT, Channel.EMAIL,
        Instant.parse("2025-01-10T00:00:00Z"));
    UUID sms = seed(AuditStatus.SENT, Channel.SMS,
        Instant.parse("2025-01-10T00:00:00Z"));

    PagedResponse<AuditTrail> result = adapter.search(
        new AuditSearchQuery(null, Channel.SMS, null, null, 0, 20));

    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.content())
        .extracting(AuditTrail::getNotificationId)
        .containsExactly(sms);
  }

  @Test
  void search_byFromAndTo_returnsOnlyTrailsInsideTheWindow() {
    seed(AuditStatus.SENT, Channel.EMAIL,
        Instant.parse("2025-01-01T00:00:00Z"));
    UUID middle = seed(AuditStatus.SENT, Channel.EMAIL,
        Instant.parse("2025-02-01T00:00:00Z"));
    UUID late = seed(AuditStatus.SENT, Channel.EMAIL,
        Instant.parse("2025-03-01T00:00:00Z"));

    PagedResponse<AuditTrail> fromOnly = adapter.search(
        new AuditSearchQuery(null, null,
            Instant.parse("2025-01-15T00:00:00Z"), null, 0, 20));
    PagedResponse<AuditTrail> toOnly = adapter.search(
        new AuditSearchQuery(null, null, null,
            Instant.parse("2025-02-15T00:00:00Z"), 0, 20));
    PagedResponse<AuditTrail> window = adapter.search(
        new AuditSearchQuery(null, null,
            Instant.parse("2025-01-15T00:00:00Z"),
            Instant.parse("2025-02-15T00:00:00Z"), 0, 20));

    assertThat(fromOnly.content())
        .extracting(AuditTrail::getNotificationId)
        .containsExactlyInAnyOrder(middle, late);
    assertThat(toOnly.totalElements()).isEqualTo(2);
    assertThat(window.content())
        .extracting(AuditTrail::getNotificationId)
        .containsExactly(middle);
  }

  @Test
  void search_withCombinedFilters_appliesAllOfThem() {
    Instant created = Instant.parse("2025-02-01T00:00:00Z");
    UUID match = seed(AuditStatus.SENT, Channel.EMAIL, created);
    seed(AuditStatus.FAILED, Channel.EMAIL, created);
    seed(AuditStatus.SENT, Channel.SMS, created);

    PagedResponse<AuditTrail> result = adapter.search(
        new AuditSearchQuery(AuditStatus.SENT, Channel.EMAIL,
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-03-01T00:00:00Z"), 0, 20));

    assertThat(result.content())
        .extracting(AuditTrail::getNotificationId)
        .containsExactly(match);
  }

  /** Inserts a trail directly so createdAt is deterministic. */
  private UUID seed(AuditStatus status, Channel channel, Instant createdAt) {
    UUID notificationId = UUID.randomUUID();
    AuditEventDocument event = new AuditEventDocument(
        UUID.randomUUID(), status.name(), "seeded", channel.name(),
        "enc", Priority.MEDIUM.name(), createdAt);
    mongoOperations.insert(new AuditTrailDocument(
        notificationId.toString(), status.name(), List.of(event),
        createdAt, createdAt));
    return notificationId;
  }
}
