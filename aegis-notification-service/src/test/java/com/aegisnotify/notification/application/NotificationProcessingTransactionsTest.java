package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.dto.TemplateRenderRequest;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import com.aegisnotify.notification.application.port.out.NotificationLogRepository;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.TemplateRenderer;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.application.service.NotificationProcessingTransactions;
import com.aegisnotify.notification.application.service.NotificationProcessingTransactions.PreparedNotification;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.exception.NotificationNotFoundException;
import com.aegisnotify.notification.domain.exception.TemplateNotFoundException;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.NotificationLog;
import com.aegisnotify.notification.domain.model.Template;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationProcessingTransactionsTest {

  @Mock
  private NotificationRepository notificationRepository;

  @Mock
  private NotificationLogRepository notificationLogRepository;

  @Mock
  private TemplateRepository templateRepository;

  @Mock
  private TemplateRenderer templateRenderer;

  @Mock
  private AuditEventPublisherPort auditEventPublisherPort;

  private NotificationProcessingTransactions transactions;

  @BeforeEach
  void setUp() {
    transactions = new NotificationProcessingTransactions(notificationRepository,
        notificationLogRepository, templateRepository, templateRenderer,
        auditEventPublisherPort);
  }

  @Test
  void prepare_returnsRenderedNotification() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );
    Template template = Template.reconstitute(
        UUID.randomUUID(), "welcome", Channel.EMAIL,
        "Welcome", "Hello {{name}}", List.of("name"),
        true, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(templateRepository.findActiveByName("welcome"))
        .thenReturn(Optional.of(template));
    when(templateRenderer.render(any(TemplateRenderRequest.class)))
        .thenReturn("Hello John");
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PreparedNotification prepared = transactions.prepare(notificationId);

    assertEquals(NotificationStatus.PROCESSING, prepared.notification().getStatus());
    assertEquals("Welcome", prepared.subject());
    assertEquals("Hello John", prepared.renderedBody());

    ArgumentCaptor<AuditEventMessage> captor = ArgumentCaptor.forClass(AuditEventMessage.class);
    Mockito.verify(auditEventPublisherPort).publish(captor.capture());
    assertEquals("PROCESSING", captor.getValue().status());

    ArgumentCaptor<TemplateRenderRequest> renderCaptor =
        ArgumentCaptor.forClass(TemplateRenderRequest.class);
    Mockito.verify(templateRenderer).render(renderCaptor.capture());
    TemplateRenderRequest request = renderCaptor.getValue();
    assertEquals("Hello {{name}}", request.templateBody());
    assertEquals(Map.of("name", "John"), request.parameters());
    assertEquals(List.of("name"), request.requiredVariables());
    assertEquals(Channel.EMAIL, request.channel());
  }

  @Test
  void prepare_notificationNotFound_throwsNotificationNotFoundException() {
    UUID notificationId = UUID.randomUUID();
    when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

    assertThrows(NotificationNotFoundException.class, () -> transactions.prepare(notificationId));
  }

  /**
   * X2 of the design: a leader notification with a pre-set {@code
   * aggregateBody} skips {@link TemplateRenderer} entirely and uses the
   * pre-summarized body directly — the one small branch this slice adds.
   * Subject still comes from the resolved template (see apply-progress for
   * the documented subject-persistence gap: the schema has no dedicated
   * aggregate-subject column).
   */
  @Test
  void prepare_leaderWithAggregateBody_skipsRenderAndUsesPreSetBody() {
    UUID notificationId = UUID.randomUUID();
    UUID aggregationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.MEDIUM, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now(), aggregationId, "Two things happened."
    );
    Template template = Template.reconstitute(
        UUID.randomUUID(), "welcome", Channel.EMAIL,
        "Welcome", "Hello {{name}}", List.of("name"),
        true, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(templateRepository.findActiveByName("welcome"))
        .thenReturn(Optional.of(template));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PreparedNotification prepared = transactions.prepare(notificationId);

    assertEquals("Welcome", prepared.subject());
    assertEquals("Two things happened.", prepared.renderedBody());
    Mockito.verifyNoInteractions(templateRenderer);
  }

  @Test
  void prepare_normalNotificationWithoutAggregateBody_takesUnchangedRenderPath() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.MEDIUM, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );
    Template template = Template.reconstitute(
        UUID.randomUUID(), "welcome", Channel.EMAIL,
        "Welcome", "Hello {{name}}", List.of("name"),
        true, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(templateRepository.findActiveByName("welcome"))
        .thenReturn(Optional.of(template));
    when(templateRenderer.render(any(TemplateRenderRequest.class)))
        .thenReturn("Hello John");
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PreparedNotification prepared = transactions.prepare(notificationId);

    assertEquals("Hello John", prepared.renderedBody());
    Mockito.verify(templateRenderer).render(any(TemplateRenderRequest.class));
  }

  @Test
  void prepare_templateNotFound_throwsTemplateNotFoundException() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "missing",
        Map.of(), Priority.HIGH, NotificationStatus.PENDING,
        null, null, Instant.now(), Instant.now()
    );

    when(notificationRepository.findById(notificationId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(templateRepository.findActiveByName("missing")).thenReturn(Optional.empty());

    assertThrows(TemplateNotFoundException.class, () -> transactions.prepare(notificationId));
  }

  @Test
  void applyResult_sentOutcome_returnsSentStatusAndPublishesAuditEvent() {
    Notification processing = Notification.reconstitute(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now()
    );
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NotificationResponse response = transactions.applyResult(processing,
        new ProviderResult(ProviderResult.Outcome.SENT, "SendGrid", null));

    assertEquals(NotificationStatus.SENT, response.status());

    ArgumentCaptor<AuditEventMessage> captor = ArgumentCaptor.forClass(AuditEventMessage.class);
    Mockito.verify(auditEventPublisherPort).publish(captor.capture());
    assertEquals("SENT", captor.getValue().status());
  }

  @Test
  void applyResult_failedOutcome_publishesProviderFailAuditStatus() {
    Notification processing = Notification.reconstitute(
        UUID.randomUUID(), Channel.SMS, "+34600000000", "otp",
        Map.of("code", "1234"), Priority.MEDIUM, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now()
    );
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NotificationResponse response = transactions.applyResult(processing,
        new ProviderResult(ProviderResult.Outcome.FAILED, "TwilioPrimary", "Connection timeout"));

    assertEquals(NotificationStatus.FAILED, response.status());

    ArgumentCaptor<AuditEventMessage> captor = ArgumentCaptor.forClass(AuditEventMessage.class);
    Mockito.verify(auditEventPublisherPort).publish(captor.capture());
    assertEquals("PROVIDER_A_FAIL", captor.getValue().status());
  }

  @Test
  void applyResult_failedCriticalOutcome_returnsFailedCriticalStatus() {
    Notification processing = Notification.reconstitute(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.LOW, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now()
    );
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NotificationResponse response = transactions.applyResult(processing,
        new ProviderResult(ProviderResult.Outcome.FAILED_CRITICAL, null,
            "All providers exhausted"));

    assertEquals(NotificationStatus.FAILED_CRITICAL, response.status());
  }

  @Test
  void applyResult_sentViaFallbackOutcome_returnsFallbackStatus() {
    Notification processing = Notification.reconstitute(
        UUID.randomUUID(), Channel.SMS, "+34600000000", "otp",
        Map.of("code", "1234"), Priority.HIGH, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now()
    );
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NotificationResponse response = transactions.applyResult(processing,
        new ProviderResult(ProviderResult.Outcome.SENT_VIA_FALLBACK, "TwilioBackup", null));

    assertEquals(NotificationStatus.SENT_VIA_FALLBACK, response.status());
  }

  /**
   * Task 3.5/3.6 (X2, D5): once the leader's actual delivery outcome is
   * known, the SAME outcome must be applied to every sibling sharing its
   * {@code aggregationId} — each sibling gets its own status update, its
   * own {@link NotificationLog}, and its own {@link AuditEventMessage},
   * every audit event's details carrying the shared aggregation id.
   *
   * <p>Fix 4 (review-resilience WARNING): sibling propagation is now its OWN
   * transaction, invoked separately from {@code applyResult} — this test
   * exercises both calls in sequence, mirroring the real caller ({@code
   * ProcessNotificationService}).</p>
   *
   * <p>Fix 5 (review-reliability WARNING): each fabricated sibling has a
   * DISTINCT recipient/channel so a bug that swapped/reused the wrong
   * notification's recipient or channel per member would be caught, not
   * just a distinct-ID count.</p>
   */
  @Test
  void applyResult_leaderWithAggregationId_propagatesOutcomeToSiblings() {
    UUID aggregationId = UUID.randomUUID();
    UUID leaderId = UUID.randomUUID();
    UUID sibling1Id = UUID.randomUUID();
    UUID sibling2Id = UUID.randomUUID();

    final Notification processing = Notification.reconstitute(
        leaderId, Channel.EMAIL, "leader@example.com", "welcome",
        Map.of("name", "John"), Priority.MEDIUM, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now(), aggregationId, "Two things happened."
    );
    Notification leaderAsStoredByAggregationId = Notification.reconstitute(
        leaderId, Channel.EMAIL, "leader@example.com", "welcome",
        Map.of("name", "John"), Priority.MEDIUM, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now(), aggregationId, "Two things happened."
    );
    Notification sibling1 = Notification.reconstitute(
        sibling1Id, Channel.EMAIL, "sibling1@example.com", "welcome",
        Map.of("name", "Jane"), Priority.MEDIUM, NotificationStatus.QUEUED,
        null, null, Instant.now(), Instant.now(), aggregationId, null
    );
    Notification sibling2 = Notification.reconstitute(
        sibling2Id, Channel.SMS, "+34600000002", "welcome",
        Map.of("name", "Jack"), Priority.MEDIUM, NotificationStatus.QUEUED,
        null, null, Instant.now(), Instant.now(), aggregationId, null
    );

    when(notificationRepository.findByAggregationId(aggregationId)).thenReturn(
        List.of(leaderAsStoredByAggregationId, sibling1, sibling2));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    transactions.applyResult(processing,
        new ProviderResult(ProviderResult.Outcome.SENT, "SendGrid", null));
    transactions.propagateOutcomeToAggregationSiblings(processing,
        new ProviderResult(ProviderResult.Outcome.SENT, "SendGrid", null));

    // 3 saves total: leader (from applyResult itself) + 2 siblings.
    Mockito.verify(notificationRepository, times(3)).save(any(Notification.class));

    ArgumentCaptor<AuditEventMessage> captor = ArgumentCaptor.forClass(AuditEventMessage.class);
    Mockito.verify(auditEventPublisherPort, times(3)).publish(captor.capture());
    List<AuditEventMessage> published = captor.getAllValues();

    assertEquals(3, published.stream().map(AuditEventMessage::notificationId).distinct().count());
    assertTrue(published.stream().allMatch(e -> "SENT".equals(e.status())));
    assertTrue(published.stream()
        .filter(e -> e.notificationId().equals(sibling1Id) || e.notificationId().equals(sibling2Id))
        .allMatch(e -> e.details().contains(aggregationId.toString())));

    // Fix 5: each published event's recipient/channel must match THAT
    // SPECIFIC member's own notification data, never another member's.
    AuditEventMessage leaderEvent = published.stream()
        .filter(e -> e.notificationId().equals(leaderId)).findFirst().orElseThrow();
    assertEquals("leader@example.com", leaderEvent.recipient());
    assertEquals("EMAIL", leaderEvent.channel());

    AuditEventMessage sibling1Event = published.stream()
        .filter(e -> e.notificationId().equals(sibling1Id)).findFirst().orElseThrow();
    assertEquals("sibling1@example.com", sibling1Event.recipient());
    assertEquals("EMAIL", sibling1Event.channel());

    AuditEventMessage sibling2Event = published.stream()
        .filter(e -> e.notificationId().equals(sibling2Id)).findFirst().orElseThrow();
    assertEquals("+34600000002", sibling2Event.recipient());
    assertEquals("SMS", sibling2Event.channel());
  }

  /**
   * Fix 1/2 (review-resilience/review-risk CRITICAL): a sibling already
   * {@code CANCELLED} must never be resurrected back to a delivered/failed
   * status when the leader's outcome propagates — its status stays {@code
   * CANCELLED} and no outcome audit event is fabricated for it.
   */
  @Test
  void propagateOutcomeToAggregationSiblings_cancelledSibling_isNotOverwritten() {
    UUID aggregationId = UUID.randomUUID();
    UUID leaderId = UUID.randomUUID();
    UUID cancelledSiblingId = UUID.randomUUID();

    Notification leader = Notification.reconstitute(
        leaderId, Channel.EMAIL, "leader@example.com", "welcome",
        Map.of("name", "John"), Priority.MEDIUM, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now(), aggregationId, "Two things happened."
    );
    Notification cancelledSibling = Notification.reconstitute(
        cancelledSiblingId, Channel.EMAIL, "cancelled@example.com", "welcome",
        Map.of("name", "Jane"), Priority.MEDIUM, NotificationStatus.CANCELLED,
        null, null, Instant.now(), Instant.now(), aggregationId, null
    );

    when(notificationRepository.findByAggregationId(aggregationId))
        .thenReturn(List.of(leader, cancelledSibling));

    transactions.propagateOutcomeToAggregationSiblings(leader,
        new ProviderResult(ProviderResult.Outcome.SENT, "SendGrid", null));

    Mockito.verify(notificationRepository, never()).save(any(Notification.class));
    Mockito.verify(notificationLogRepository, never()).save(any(NotificationLog.class));
    Mockito.verify(auditEventPublisherPort, never()).publish(any(AuditEventMessage.class));
  }

  /**
   * Fix 1/2 (review-resilience/review-risk CRITICAL): a sibling already in a
   * terminal state (simulating the stale-retry scenario — already {@code
   * SENT} from an earlier propagation) must not be re-overwritten by a
   * second, unrelated propagation call re-triggered via a stale {@code
   * aggregationId}.
   */
  @Test
  void propagateOutcomeToAggregationSiblings_alreadyFinalizedSibling_isNotReOverwritten() {
    UUID aggregationId = UUID.randomUUID();
    UUID leaderId = UUID.randomUUID();
    UUID finalizedSiblingId = UUID.randomUUID();

    Notification leader = Notification.reconstitute(
        leaderId, Channel.EMAIL, "leader@example.com", "welcome",
        Map.of("name", "John"), Priority.MEDIUM, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now(), aggregationId, "Two things happened."
    );
    Notification alreadySentSibling = Notification.reconstitute(
        finalizedSiblingId, Channel.EMAIL, "finalized@example.com", "welcome",
        Map.of("name", "Jane"), Priority.MEDIUM, NotificationStatus.SENT,
        "SendGrid", null, Instant.now(), Instant.now(), aggregationId, null
    );

    when(notificationRepository.findByAggregationId(aggregationId))
        .thenReturn(List.of(leader, alreadySentSibling));

    transactions.propagateOutcomeToAggregationSiblings(leader,
        new ProviderResult(ProviderResult.Outcome.FAILED, "SendGridRetry", "unrelated failure"));

    Mockito.verify(notificationRepository, never()).save(any(Notification.class));
    Mockito.verify(notificationLogRepository, never()).save(any(NotificationLog.class));
    Mockito.verify(auditEventPublisherPort, never()).publish(any(AuditEventMessage.class));
  }

  /**
   * Fix 6 (review-reliability WARNING): a synchronously-throwing {@link
   * AuditEventPublisherPort} implementation (a hypothetical one that
   * violates the port's fire-and-forget javadoc contract) must not abort
   * fan-out for the remaining siblings, and must not affect the throwing
   * sibling's own already-completed save/log either.
   */
  @Test
  void propagateOutcomeToAggregationSiblings_onePublishThrows_othersStillProcessed() {
    UUID aggregationId = UUID.randomUUID();
    UUID leaderId = UUID.randomUUID();
    UUID sibling1Id = UUID.randomUUID();
    UUID sibling2Id = UUID.randomUUID();

    Notification leader = Notification.reconstitute(
        leaderId, Channel.EMAIL, "leader@example.com", "welcome",
        Map.of("name", "John"), Priority.MEDIUM, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now(), aggregationId, "Two things happened."
    );
    Notification sibling1 = Notification.reconstitute(
        sibling1Id, Channel.EMAIL, "sibling1@example.com", "welcome",
        Map.of("name", "Jane"), Priority.MEDIUM, NotificationStatus.QUEUED,
        null, null, Instant.now(), Instant.now(), aggregationId, null
    );
    Notification sibling2 = Notification.reconstitute(
        sibling2Id, Channel.SMS, "+34600000002", "welcome",
        Map.of("name", "Jack"), Priority.MEDIUM, NotificationStatus.QUEUED,
        null, null, Instant.now(), Instant.now(), aggregationId, null
    );

    when(notificationRepository.findByAggregationId(aggregationId))
        .thenReturn(List.of(leader, sibling1, sibling2));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    Mockito.doAnswer(invocation -> {
      AuditEventMessage event = invocation.getArgument(0);
      if (event.notificationId().equals(sibling1Id)) {
        throw new RuntimeException("boom - simulated synchronous publish failure");
      }
      return null;
    }).when(auditEventPublisherPort).publish(any(AuditEventMessage.class));

    assertDoesNotThrow(() -> transactions.propagateOutcomeToAggregationSiblings(leader,
        new ProviderResult(ProviderResult.Outcome.SENT, "SendGrid", null)));

    // Both siblings' saves/logs still happened even though sibling1's
    // publish threw — the delivery-critical work is unaffected by the
    // audit-publish failure.
    Mockito.verify(notificationRepository, times(2)).save(any(Notification.class));
    Mockito.verify(notificationLogRepository, times(2)).save(any(NotificationLog.class));
    // sibling1's throwing publish call still counts as an attempted publish;
    // sibling2's succeeds normally.
    Mockito.verify(auditEventPublisherPort, times(2)).publish(any(AuditEventMessage.class));
  }

  /**
   * Regression proof (never accidentally widen the blast radius): a normal,
   * non-aggregated notification (no {@code aggregationId}) must not query
   * siblings at all and must publish exactly ONE audit event, byte-identical
   * to pre-Slice-3 behavior.
   */
  @Test
  void applyResult_noAggregationId_neverQueriesSiblings_publishesExactlyOneAuditEvent() {
    Notification processing = Notification.reconstitute(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now()
    );
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(notificationLogRepository.save(any(NotificationLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    transactions.applyResult(processing,
        new ProviderResult(ProviderResult.Outcome.SENT, "SendGrid", null));

    Mockito.verify(notificationRepository, never()).findByAggregationId(any());
    Mockito.verify(auditEventPublisherPort, times(1)).publish(any(AuditEventMessage.class));
  }
}
