package com.aegisnotify.audit.infrastructure.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for {@link AuditTrailDocument}.
 *
 * <p>The document ID is the {@code notificationId} string.</p>
 */
public interface SpringDataAuditTrailRepository
    extends MongoRepository<AuditTrailDocument, String> {
}
