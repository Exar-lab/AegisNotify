package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.dto.TemplateRenderRequest;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.TemplateRenderer;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.exception.NotificationNotFoundException;
import com.aegisnotify.notification.domain.exception.TemplateNotFoundException;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.domain.model.Template;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the two short-lived database transactions that bracket notification
 * processing, so that {@link ProcessNotificationService} can invoke the
 * blocking provider HTTP call with no transaction open. Holding a DB
 * connection for the duration of an external network call would starve the
 * connection pool under load — template lookup and rendering are local and
 * safe to keep inside {@link #prepare}, but delivery itself never is.
 */
@Service
public class NotificationProcessingTransactions {

  private static final Logger log =
      LoggerFactory.getLogger(NotificationProcessingTransactions.class);

  private final NotificationRepository notificationRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final TemplateRepository templateRepository;
  private final TemplateRenderer templateRenderer;
  private final AuditEventPublisherPort auditEventPublisherPort;

  public NotificationProcessingTransactions(NotificationRepository notificationRepository,
      NotificationLogRepository notificationLogRepository,
      TemplateRepository templateRepository,
      TemplateRenderer templateRenderer,
      AuditEventPublisherPort auditEventPublisherPort) {
    this.notificationRepository = notificationRepository;
    this.notificationLogRepository = notificationLogRepository;
    this.templateRepository = templateRepository;
    this.templateRenderer = templateRenderer;
    this.auditEventPublisherPort = auditEventPublisherPort;
  }

  @Transactional
  public PreparedNotification prepare(UUID notificationId) {
    Notification notification = notificationRepository.findById(notificationId)
        .orElseThrow(() -> new NotificationNotFoundException(notificationId));

    Notification processing = notification.markProcessing();
    notificationRepository.save(processing);
    notificationLogRepository.save(
        NotificationLog.create(notificationId, LogStatus.PROCESSING, "Processing started")
    );

    publishAuditEvent(processing, AuditStatusMapper.toAuditStatus(processing.getStatus()),
        "Processing started");

    Template template = templateRepository.findActiveByName(processing.getTemplateName())
        .orElseThrow(() -> new TemplateNotFoundException(processing.getTemplateName()));

    // X2 of the design: a leader notification carries a pre-summarized
    // aggregate_body (set by the aggregation flush path, issue #86 Slice 2)
    // and skips the renderer entirely — the template is still looked up for
    // its subject line, since no dedicated aggregate-subject column exists.
    if (processing.getAggregateBody() != null) {
      return new PreparedNotification(processing, template.getSubject(),
          processing.getAggregateBody());
    }

    TemplateRenderRequest request = new TemplateRenderRequest(
        template.getBody(), processing.getParameters(), template.getVariables(),
        template.getChannel());
    String renderedBody = templateRenderer.render(request);

    return new PreparedNotification(processing, template.getSubject(), renderedBody);
  }

  @Transactional
  public NotificationResponse applyResult(Notification processing, ProviderResult result) {
    Notification updated = applyOutcomeAndRecord(processing, result, "");
    return new NotificationResponse(updated.getId(), updated.getStatus());
  }

  /**
   * X2 of the design (Slice 3, issue #86): once the leader notification's
   * actual delivery outcome is known, the SAME outcome must be applied to
   * every sibling folded into the same aggregate. Siblings never get their
   * own outbox event or their own provider call (that is the entire point
   * of aggregation, X2) — {@code AggregationFlushTransactions#flushAggregate}
   * (Slice 2/3) only advances a sibling to {@code QUEUED} at flush time.
   * Without this propagation a sibling would stay {@code QUEUED} forever,
   * with no terminal status and no delivery-outcome audit trail of its own.
   *
   * <p>Each sibling gets its own {@link NotificationLog} entry and its own
   * {@link AuditEventMessage} (D5), so the aggregate's audit trail stays
   * individually traceable per original notification through to the FINAL
   * outcome, not just at flush time.</p>
   *
   * <p>Runs in its OWN transaction, separate from and invoked AFTER {@link
   * #applyResult} has already committed the leader's own delivery-critical
   * status update (review-resilience WARNING): the sibling group can grow up
   * to {@code max-group-size} (default 20), and a slow/failing sibling save
   * must never roll back — or hold the connection behind — the leader's
   * already-correct, already-committed outcome. A retried delivery
   * re-applies the same idempotent outcome to the same siblings, the same
   * at-least-once tolerance already accepted for the leader itself (K3 of
   * the design).</p>
   *
   * <p>Guarded per-sibling by current status (review-resilience/review-risk
   * CRITICAL fix): only a sibling still {@code QUEUED} — the expected state
   * immediately after {@code flushAggregate} and before any outcome has ever
   * reached it — has the outcome applied. A sibling is skipped, untouched,
   * when it is NOT {@code QUEUED}, which covers two distinct bugs with one
   * guard: (1) a sibling the user already cancelled via {@code
   * CancelNotificationService} (now {@code CANCELLED}) is never resurrected
   * back to a delivered/failed status, and (2) a sibling from a stale,
   * already-finalized aggregation — reachable if a notification is later
   * retried via {@code RetryFailedNotificationService} without clearing its
   * old {@code aggregationId} — is never re-overwritten by an unrelated
   * retry's outcome, since it is no longer {@code QUEUED} once its own
   * aggregate's outcome was first (correctly) propagated to it.</p>
   *
   * <p>No-op when {@code leader} carries no {@code aggregationId} (the
   * overwhelmingly common case — a notification that was never aggregated
   * at all).</p>
   */
  @Transactional
  public void propagateOutcomeToAggregationSiblings(Notification leader, ProviderResult result) {
    UUID aggregationId = leader.getAggregationId();
    if (aggregationId == null) {
      return;
    }

    for (Notification sibling : notificationRepository.findByAggregationId(aggregationId)) {
      if (sibling.getId().equals(leader.getId())) {
        continue;
      }

      if (sibling.getStatus() != NotificationStatus.QUEUED) {
        log.warn("Skipping aggregation outcome propagation for notification {} in aggregation "
                + "{}: current status {} is not QUEUED (already cancelled or already finalized "
                + "by an earlier propagation)",
            sibling.getId(), aggregationId, sibling.getStatus());
        continue;
      }

      applyOutcomeAndRecord(sibling, result, " (aggregation " + aggregationId + ")");
    }
  }

  /**
   * Shared 4-step outcome-application sequence (review-readability WARNING
   * fix): apply the provider outcome to the domain object, persist it,
   * record its {@link NotificationLog}, and publish its {@link
   * AuditEventMessage} — used identically by the leader's own outcome (from
   * {@link #applyResult}) and by every sibling in {@link
   * #propagateOutcomeToAggregationSiblings}, differing only in the
   * free-text audit-detail suffix.
   *
   * <p>The audit publish itself is wrapped so a publish failure can never
   * abort this sequence or, in the sibling loop, prevent the remaining
   * siblings from being processed — {@link AuditEventPublisherPort} is
   * fire-and-forget by contract, but nothing about the interface itself
   * enforces that at compile time, so this is defense-in-depth rather than
   * reliance on a single concrete adapter's behavior.</p>
   */
  private Notification applyOutcomeAndRecord(Notification notification, ProviderResult result,
      String auditDetailSuffix) {
    Notification updated = applyProviderResult(notification, result);
    notificationRepository.save(updated);
    notificationLogRepository.save(
        NotificationLog.create(updated.getId(), toLogStatus(result.outcome()),
            buildLogDetail(result))
    );

    publishAuditEventSafely(updated, toAuditStatusFromResult(result),
        buildLogDetail(result) + auditDetailSuffix);

    return updated;
  }

  private Notification applyProviderResult(Notification notification, ProviderResult result) {
    return switch (result.outcome()) {
      case SENT -> notification.markSent(result.providerName());
      case SENT_VIA_FALLBACK -> notification.markSentViaFallback(result.providerName());
      case FAILED -> notification.markFailed(result.errorDetail());
      case FAILED_CRITICAL -> notification.markFailedCritical(result.errorDetail());
    };
  }

  private LogStatus toLogStatus(ProviderResult.Outcome outcome) {
    return switch (outcome) {
      case SENT -> LogStatus.SENT;
      case SENT_VIA_FALLBACK -> LogStatus.SENT_VIA_FALLBACK;
      case FAILED -> LogStatus.FAILED;
      case FAILED_CRITICAL -> LogStatus.FAILED_CRITICAL;
    };
  }

  private String buildLogDetail(ProviderResult result) {
    return switch (result.outcome()) {
      case SENT -> "Sent via " + result.providerName();
      case SENT_VIA_FALLBACK -> "Sent via fallback provider " + result.providerName();
      case FAILED -> "Delivery failed: " + result.errorDetail();
      case FAILED_CRITICAL -> "Critical failure, sent to DLQ: " + result.errorDetail();
    };
  }

  private String toAuditStatusFromResult(ProviderResult result) {
    return switch (result.outcome()) {
      case SENT -> AuditStatusMapper.toAuditStatus(NotificationStatus.SENT);
      case SENT_VIA_FALLBACK ->
          AuditStatusMapper.toAuditStatus(NotificationStatus.SENT_VIA_FALLBACK);
      case FAILED -> AuditStatusMapper.toProviderFailStatus(true);
      case FAILED_CRITICAL -> AuditStatusMapper.toAuditStatus(NotificationStatus.FAILED_CRITICAL);
    };
  }

  private void publishAuditEvent(Notification notification, String status, String details) {
    auditEventPublisherPort.publish(new AuditEventMessage(
        notification.getId(),
        status,
        details,
        notification.getChannel().name(),
        notification.getRecipient(),
        notification.getPriority().name(),
        Instant.now()
    ));
  }

  /**
   * Same publish as {@link #publishAuditEvent} but never lets a publish
   * failure escape (review-reliability WARNING fix) — used by {@link
   * #applyOutcomeAndRecord} so one bad audit publish in {@link
   * #propagateOutcomeToAggregationSiblings}'s fan-out loop can never abort
   * processing of the remaining siblings in the same group.
   */
  private void publishAuditEventSafely(Notification notification, String status, String details) {
    try {
      publishAuditEvent(notification, status, details);
    } catch (Exception ex) {
      log.warn("Failed to publish audit event for notification {} (status={}): {}",
          notification.getId(), status, ex.getMessage(), ex);
    }
  }

  public record PreparedNotification(Notification notification, String subject,
      String renderedBody) {
  }
}
