package com.aegisnotify.audit.infrastructure.persistence.mongo;

import com.aegisnotify.audit.application.dto.AuditSearchQuery;
import com.aegisnotify.audit.application.dto.PagedResponse;
import com.aegisnotify.audit.application.port.out.AuditEventRepository;
import com.aegisnotify.audit.domain.model.AuditEvent;
import com.aegisnotify.audit.domain.model.AuditTrail;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * MongoDB implementation of the AuditEventRepository port.
 *
 * <p>Uses one document per notificationId ({@code _id}) with an embedded
 * {@code events[]} array. New events are appended via {@code $push}
 * (atomic append). The {@code currentStatus} and {@code updatedAt}
 * fields are updated on each append. If the document does not exist,
 * an upsert creates it.</p>
 */
@Component
public class MongoAuditEventRepositoryAdapter implements AuditEventRepository {

  private final SpringDataAuditTrailRepository springDataRepository;
  private final MongoOperations mongoTemplate;
  private final AuditPersistenceMapper mapper;

  public MongoAuditEventRepositoryAdapter(
      SpringDataAuditTrailRepository springDataRepository,
      MongoOperations mongoTemplate,
      AuditPersistenceMapper mapper) {
    this.springDataRepository = springDataRepository;
    this.mongoTemplate = mongoTemplate;
    this.mapper = mapper;
  }

  @Override
  public void appendToTrail(AuditEvent event) {
    AuditEventDocument eventDoc = mapper.toEventDocument(event);
    String notificationId = event.getNotificationId().toString();
    Instant now = Instant.now();

    Query query = new Query(
        Criteria.where("_id").is(notificationId));

    Update update = new Update()
        .push("events", eventDoc)
        .set("currentStatus", event.getStatus().name())
        .set("updatedAt", now)
        .setOnInsert("createdAt", now);

    mongoTemplate.upsert(query, update, AuditTrailDocument.class);
  }

  @Override
  public Optional<AuditTrail> findByNotificationId(UUID notificationId) {
    return springDataRepository.findById(notificationId.toString())
        .map(mapper::toDomain);
  }

  @Override
  public PagedResponse<AuditTrail> search(AuditSearchQuery searchQuery) {
    Query query = buildSearchQuery(searchQuery);

    long totalElements = mongoTemplate.count(query,
        AuditTrailDocument.class);

    int effectiveSize = Math.max(1, Math.min(searchQuery.size(), 100));
    query.skip((long) searchQuery.page() * effectiveSize);
    query.limit(effectiveSize);

    List<AuditTrailDocument> documents =
        mongoTemplate.find(query, AuditTrailDocument.class);

    List<AuditTrail> trails = documents.stream()
        .map(mapper::toDomain)
        .toList();

    int totalPages =
        (int) Math.ceil((double) totalElements / effectiveSize);

    return new PagedResponse<>(
        trails,
        searchQuery.page(),
        effectiveSize,
        totalElements,
        totalPages
    );
  }

  private Query buildSearchQuery(AuditSearchQuery searchQuery) {
    Query query = new Query();

    if (searchQuery.status() != null) {
      query.addCriteria(
          Criteria.where("currentStatus")
              .is(searchQuery.status().name()));
    }

    if (searchQuery.channel() != null) {
      query.addCriteria(
          Criteria.where("events.channel")
              .is(searchQuery.channel().name()));
    }

    if (searchQuery.from() != null) {
      query.addCriteria(
          Criteria.where("createdAt").gte(searchQuery.from()));
    }

    if (searchQuery.to() != null) {
      query.addCriteria(
          Criteria.where("createdAt").lte(searchQuery.to()));
    }

    return query;
  }
}
