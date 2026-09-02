package com.aegisnotify.notification.infrastructure.persistence.repository;

import com.aegisnotify.notification.domain.enums.AggregationBufferStatus;
import com.aegisnotify.notification.infrastructure.persistence.entity.AggregationBufferJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataAggregationBufferRepository
    extends JpaRepository<AggregationBufferJpaEntity, UUID> {

  /**
   * Rows still {@code BUFFERED} whose window has expired, plus rows stuck
   * {@code CLAIMED} past their lease cutoff (B3 reclaim path).
   */
  @Query("SELECT e FROM AggregationBufferJpaEntity e WHERE "
      + "(e.status = com.aegisnotify.notification.domain.enums.AggregationBufferStatus.BUFFERED "
      + "AND e.expiresAt <= :now) OR "
      + "(e.status = com.aegisnotify.notification.domain.enums.AggregationBufferStatus.CLAIMED "
      + "AND e.claimedAt <= :leaseCutoff)")
  List<AggregationBufferJpaEntity> findClaimable(@Param("now") Instant now,
      @Param("leaseCutoff") Instant leaseCutoff);

  /**
   * Conditional update: only succeeds (returns 1) if the row's status still
   * matches {@code expectedStatus} at the moment of the update — the
   * single-claimant guard against a concurrent claimer racing on the same
   * group (B3).
   */
  @Modifying
  @Query("UPDATE AggregationBufferJpaEntity e SET e.status = :newStatus, "
      + "e.claimedAt = :claimedAt, e.attempts = :attempts "
      + "WHERE e.id = :id AND e.status = :expectedStatus")
  int conditionalClaim(@Param("id") UUID id,
      @Param("expectedStatus") AggregationBufferStatus expectedStatus,
      @Param("newStatus") AggregationBufferStatus newStatus,
      @Param("claimedAt") Instant claimedAt,
      @Param("attempts") int attempts);

  @Modifying
  @Query("UPDATE AggregationBufferJpaEntity e SET e.status = "
      + "com.aegisnotify.notification.domain.enums.AggregationBufferStatus.DONE WHERE e.id = :id")
  void markDone(@Param("id") UUID id);
}
