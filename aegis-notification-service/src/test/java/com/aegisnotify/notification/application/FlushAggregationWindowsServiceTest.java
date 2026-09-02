package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.SummarizationRequest;
import com.aegisnotify.notification.application.dto.SummarizedContent;
import com.aegisnotify.notification.application.dto.TemplateRenderRequest;
import com.aegisnotify.notification.application.port.out.AggregationBufferRepository;
import com.aegisnotify.notification.application.port.out.AggregationSummarizerPort;
import com.aegisnotify.notification.application.port.out.NotificationMetricsPort;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.TemplateRenderer;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.application.service.AggregationFlushTransactions;
import com.aegisnotify.notification.application.service.FlushAggregationWindowsService;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.exception.SummarizerUnavailableException;
import com.aegisnotify.notification.domain.model.AggregationSettings;
import com.aegisnotify.notification.domain.model.BufferedNotification;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.Template;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers {@link FlushAggregationWindowsService}'s orchestration: it delegates
 * claim/resolve work to {@link AggregationFlushTransactions} per row without
 * holding a shared transaction, computes the claimable window from an
 * injected {@link Clock} (so expiry boundary behavior is deterministic), and
 * — since Slice 1 has no summarizer — always resolves a successfully claimed
 * row via individual delivery.
 */
@ExtendWith(MockitoExtension.class)
class FlushAggregationWindowsServiceTest {

  @Mock
  private AggregationBufferRepository aggregationBufferRepository;

  @Mock
  private AggregationFlushTransactions transactions;

  @Mock
  private NotificationMetricsPort metrics;

  @Mock
  private NotificationRepository notificationRepository;

  @Mock
  private TemplateRepository templateRepository;

  @Mock
  private TemplateRenderer templateRenderer;

  @Mock
  private AggregationSummarizerPort summarizerPort;

  private static final Instant FIXED_NOW = Instant.parse("2026-09-01T12:00:00Z");
  private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
  private final AggregationSettings settings = new AggregationSettings(
      true, Duration.ofMinutes(5), true, 20, Set.of(), Set.of(), Duration.ofMinutes(2), 3);

  private FlushAggregationWindowsService service() {
    return new FlushAggregationWindowsService(aggregationBufferRepository, transactions,
        settings, clock, metrics, notificationRepository, templateRepository, templateRenderer,
        summarizerPort);
  }

  @Test
  void flushExpiredWindows_computesClaimableWindowFromInjectedClock() {
    when(aggregationBufferRepository.findClaimable(any(), any())).thenReturn(List.of());

    service().flushExpiredWindows();

    Instant expectedLeaseCutoff = FIXED_NOW.minus(Duration.ofMinutes(2));
    verify(aggregationBufferRepository).findClaimable(eq(FIXED_NOW), eq(expectedLeaseCutoff));
  }

  @Test
  void flushExpiredWindows_noClaimableRows_returnsZero() {
    when(aggregationBufferRepository.findClaimable(any(), any())).thenReturn(List.of());

    int resolved = service().flushExpiredWindows();

    assertEquals(0, resolved);
    verify(transactions, never()).claim(any(), any());
  }

  @Test
  void flushExpiredWindows_claimableRow_claimedAndFlushedIndividually() {
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));
    BufferedNotification claimed = buffered.claim(FIXED_NOW);

    when(aggregationBufferRepository.findClaimable(any(), any())).thenReturn(List.of(buffered));
    when(transactions.claim(buffered, FIXED_NOW)).thenReturn(Optional.of(claimed));

    int resolved = service().flushExpiredWindows();

    assertEquals(1, resolved);
    verify(transactions).claim(buffered, FIXED_NOW);
    verify(transactions).flushIndividually(claimed);
  }

  @Test
  void flushExpiredWindows_lostClaimRace_skipsRow_doesNotFlush() {
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));

    when(aggregationBufferRepository.findClaimable(any(), any())).thenReturn(List.of(buffered));
    when(transactions.claim(buffered, FIXED_NOW)).thenReturn(Optional.empty());

    int resolved = service().flushExpiredWindows();

    assertEquals(0, resolved);
    verify(transactions, never()).flushIndividually(any());
  }

  @Test
  void flushExpiredWindows_multipleRows_eachIndependentlyClaimedAndFlushed() {
    BufferedNotification buffered1 = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "a@example.com", "welcome",
        Priority.MEDIUM, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));
    BufferedNotification buffered2 = BufferedNotification.create(
        UUID.randomUUID(), Channel.SMS, "+34600000000", "otp",
        Priority.LOW, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));
    BufferedNotification claimed1 = buffered1.claim(FIXED_NOW);
    BufferedNotification claimed2 = buffered2.claim(FIXED_NOW);

    when(aggregationBufferRepository.findClaimable(any(), any()))
        .thenReturn(List.of(buffered1, buffered2));
    when(transactions.claim(buffered1, FIXED_NOW)).thenReturn(Optional.of(claimed1));
    when(transactions.claim(buffered2, FIXED_NOW)).thenReturn(Optional.of(claimed2));

    int resolved = service().flushExpiredWindows();

    assertEquals(2, resolved);
    verify(transactions).flushIndividually(claimed1);
    verify(transactions).flushIndividually(claimed2);
    verify(transactions, times(2)).claim(any(), eq(FIXED_NOW));
  }

  /**
   * Poison-group guard (B3): Slice 1 has no summarizer to skip, so an
   * over-attempted row still resolves via individual delivery exactly like
   * any other claimed row — the guard's only behavioral effect (bypassing a
   * summarizer call) is Slice 2 scope.
   */
  @Test
  void flushExpiredWindows_rowPastMaxAttempts_stillFlushedIndividually_neverDropped() {
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));
    BufferedNotification pastMaxAttempts = buffered.claim(FIXED_NOW.minusSeconds(200))
        .claim(FIXED_NOW.minusSeconds(150))
        .claim(FIXED_NOW.minusSeconds(100))
        .claim(FIXED_NOW.minusSeconds(50));
    org.junit.jupiter.api.Assertions.assertTrue(pastMaxAttempts.hasExceededMaxAttempts(3));

    when(aggregationBufferRepository.findClaimable(any(), any()))
        .thenReturn(List.of(pastMaxAttempts));
    when(transactions.claim(pastMaxAttempts, FIXED_NOW))
        .thenReturn(Optional.of(pastMaxAttempts.claim(FIXED_NOW)));

    int resolved = service().flushExpiredWindows();

    assertEquals(1, resolved);
    verify(transactions).flushIndividually(any(BufferedNotification.class));
  }

  /**
   * A failure resolving one row must never abort the rest of the batch —
   * mirrors {@code PublishOutboxEventServiceTest}'s
   * {@code publishPending_middleEventFails_doesNotRollBackOrBlockOtherEvents}.
   */
  @Test
  void flushExpiredWindows_middleRowFlushFails_doesNotBlockOtherRows() {
    BufferedNotification buffered1 = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "a@example.com", "welcome",
        Priority.MEDIUM, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));
    BufferedNotification buffered2 = BufferedNotification.create(
        UUID.randomUUID(), Channel.SMS, "+34600000000", "otp",
        Priority.LOW, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));
    BufferedNotification buffered3 = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "c@example.com", "welcome",
        Priority.MEDIUM, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));
    BufferedNotification claimed1 = buffered1.claim(FIXED_NOW);
    BufferedNotification claimed2 = buffered2.claim(FIXED_NOW);
    BufferedNotification claimed3 = buffered3.claim(FIXED_NOW);

    when(aggregationBufferRepository.findClaimable(any(), any()))
        .thenReturn(List.of(buffered1, buffered2, buffered3));
    when(transactions.claim(buffered1, FIXED_NOW)).thenReturn(Optional.of(claimed1));
    when(transactions.claim(buffered2, FIXED_NOW)).thenReturn(Optional.of(claimed2));
    when(transactions.claim(buffered3, FIXED_NOW)).thenReturn(Optional.of(claimed3));
    // All three explicitly stubbed: under Mockito's strict-stubs default, a
    // void method with ANY stubbing defined throws PotentialStubbingProblem
    // on an unstubbed-argument call, rather than silently no-op'ing.
    org.mockito.Mockito.doNothing().when(transactions).flushIndividually(claimed1);
    org.mockito.Mockito.doThrow(new RuntimeException("db write failed"))
        .when(transactions).flushIndividually(claimed2);
    org.mockito.Mockito.doNothing().when(transactions).flushIndividually(claimed3);

    int resolved = service().flushExpiredWindows();

    assertEquals(2, resolved);
    verify(transactions).flushIndividually(claimed1);
    verify(transactions).flushIndividually(claimed2);
    verify(transactions).flushIndividually(claimed3);
    // Row 2's failure is under maxAttempts (1 <= 3): left CLAIMED for a
    // future reclaim attempt, never forced to a terminal state prematurely.
    verify(transactions, never()).forcePoisonRowDone(any(), any());
  }

  /**
   * A row whose flush attempt fails AND has exceeded maxAttempts must reach
   * a terminal DONE state instead of looping CLAIMED -> lease-expiry ->
   * reclaimed forever (B3 poison-group guard).
   */
  @Test
  void flushExpiredWindows_rowExceedsMaxAttemptsAndFlushFails_forcedToTerminalState() {
    BufferedNotification buffered = BufferedNotification.create(
        UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Priority.MEDIUM, FIXED_NOW.minusSeconds(1), FIXED_NOW.minusSeconds(300));
    BufferedNotification pastMaxAttempts = buffered.claim(FIXED_NOW.minusSeconds(200))
        .claim(FIXED_NOW.minusSeconds(150))
        .claim(FIXED_NOW.minusSeconds(100))
        .claim(FIXED_NOW.minusSeconds(50));
    BufferedNotification reclaimed = pastMaxAttempts.claim(FIXED_NOW);
    org.junit.jupiter.api.Assertions.assertTrue(reclaimed.hasExceededMaxAttempts(3));

    when(aggregationBufferRepository.findClaimable(any(), any()))
        .thenReturn(List.of(pastMaxAttempts));
    when(transactions.claim(pastMaxAttempts, FIXED_NOW)).thenReturn(Optional.of(reclaimed));
    org.mockito.Mockito.doThrow(new RuntimeException("db write failed"))
        .when(transactions).flushIndividually(reclaimed);

    int resolved = service().flushExpiredWindows();

    assertEquals(0, resolved);
    verify(transactions).forcePoisonRowDone(eq(reclaimed), any());
  }

  // --- Slice 2: summarizer-backed group flush ---

  private BufferedNotification groupRow(UUID notificationId, Instant createdAt) {
    return BufferedNotification.create(notificationId, Channel.EMAIL, "user@example.com",
        "welcome", Priority.MEDIUM, FIXED_NOW.minusSeconds(1), createdAt);
  }

  private Notification notificationFor(UUID id, Instant createdAt) {
    return Notification.reconstitute(id, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "Jane"), Priority.MEDIUM, NotificationStatus.PENDING, null, null,
        createdAt, createdAt);
  }

  private Template welcomeTemplate() {
    return Template.reconstitute(UUID.randomUUID(), "welcome", Channel.EMAIL, "Welcome",
        "Hello {{name}}", List.of("name"), true, Instant.now(), Instant.now());
  }

  /**
   * Aggregate-success path (Slice 2, task 2.12): a two-member group that
   * both claims and renders successfully, with a summarizer that succeeds,
   * resolves via exactly ONE {@link AggregationFlushTransactions#flushAggregate}
   * call — never two individual {@code flushIndividually} calls.
   */
  @Test
  void flushExpiredWindows_twoMemberGroupSummarizes_flushesAggregateOnce() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    BufferedNotification row1 = groupRow(id1, FIXED_NOW.minusSeconds(300));
    BufferedNotification row2 = groupRow(id2, FIXED_NOW.minusSeconds(200));
    BufferedNotification claimed1 = row1.claim(FIXED_NOW);
    BufferedNotification claimed2 = row2.claim(FIXED_NOW);

    when(aggregationBufferRepository.findClaimable(any(), any()))
        .thenReturn(List.of(row1, row2));
    when(transactions.claim(row1, FIXED_NOW)).thenReturn(Optional.of(claimed1));
    when(transactions.claim(row2, FIXED_NOW)).thenReturn(Optional.of(claimed2));
    when(notificationRepository.findById(id1))
        .thenReturn(Optional.of(notificationFor(id1, FIXED_NOW.minusSeconds(300))));
    when(notificationRepository.findById(id2))
        .thenReturn(Optional.of(notificationFor(id2, FIXED_NOW.minusSeconds(200))));
    when(templateRepository.findActiveByName("welcome")).thenReturn(Optional.of(welcomeTemplate()));
    when(templateRenderer.render(any(TemplateRenderRequest.class))).thenReturn("Hello Jane");
    SummarizedContent summary = new SummarizedContent("Update", "Two things happened.");
    when(summarizerPort.summarize(any(SummarizationRequest.class))).thenReturn(summary);

    int resolved = service().flushExpiredWindows();

    assertEquals(2, resolved);
    verify(transactions).flushAggregate(List.of(claimed1, claimed2), claimed1, summary);
    verify(transactions, never()).flushIndividually(any());

    ArgumentCaptor<SummarizationRequest> requestCaptor =
        ArgumentCaptor.forClass(SummarizationRequest.class);
    verify(summarizerPort).summarize(requestCaptor.capture());
    assertEquals(List.of("Hello Jane", "Hello Jane"), requestCaptor.getValue().renderedBodies());
  }

  /**
   * Never-drop guarantee (Slice 2, task 2.10): ANY summarizer failure —
   * here, {@link SummarizerUnavailableException} standing in for timeout,
   * open breaker, or an error response, since the adapter collapses every
   * one of those into this single exception type — must fall EVERY member
   * of the group back to individual delivery. Zero drops, zero aggregate
   * writes.
   */
  @Test
  void flushExpiredWindows_summarizerUnavailable_fallsBackWholeGroupToIndividualDelivery() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    BufferedNotification row1 = groupRow(id1, FIXED_NOW.minusSeconds(300));
    BufferedNotification row2 = groupRow(id2, FIXED_NOW.minusSeconds(200));
    BufferedNotification claimed1 = row1.claim(FIXED_NOW);
    BufferedNotification claimed2 = row2.claim(FIXED_NOW);

    when(aggregationBufferRepository.findClaimable(any(), any()))
        .thenReturn(List.of(row1, row2));
    when(transactions.claim(row1, FIXED_NOW)).thenReturn(Optional.of(claimed1));
    when(transactions.claim(row2, FIXED_NOW)).thenReturn(Optional.of(claimed2));
    when(notificationRepository.findById(id1))
        .thenReturn(Optional.of(notificationFor(id1, FIXED_NOW.minusSeconds(300))));
    when(notificationRepository.findById(id2))
        .thenReturn(Optional.of(notificationFor(id2, FIXED_NOW.minusSeconds(200))));
    when(templateRepository.findActiveByName("welcome")).thenReturn(Optional.of(welcomeTemplate()));
    when(templateRenderer.render(any(TemplateRenderRequest.class))).thenReturn("Hello Jane");
    when(summarizerPort.summarize(any(SummarizationRequest.class)))
        .thenThrow(new SummarizerUnavailableException("aggregation-agent circuit breaker is open"));

    int resolved = service().flushExpiredWindows();

    assertEquals(2, resolved);
    verify(transactions, never()).flushAggregate(any(), any(), any());
    verify(transactions).flushIndividually(claimed1);
    verify(transactions).flushIndividually(claimed2);
  }

  /**
   * {@code flushAggregate} itself failing (e.g. a DB write error) must still
   * fall every member of the group back to individual delivery — the
   * never-drop guarantee holds even after a successful summarize() call.
   */
  @Test
  void flushExpiredWindows_flushAggregateFails_fallsBackWholeGroupToIndividualDelivery() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    BufferedNotification row1 = groupRow(id1, FIXED_NOW.minusSeconds(300));
    BufferedNotification row2 = groupRow(id2, FIXED_NOW.minusSeconds(200));
    BufferedNotification claimed1 = row1.claim(FIXED_NOW);
    BufferedNotification claimed2 = row2.claim(FIXED_NOW);

    when(aggregationBufferRepository.findClaimable(any(), any()))
        .thenReturn(List.of(row1, row2));
    when(transactions.claim(row1, FIXED_NOW)).thenReturn(Optional.of(claimed1));
    when(transactions.claim(row2, FIXED_NOW)).thenReturn(Optional.of(claimed2));
    when(notificationRepository.findById(id1))
        .thenReturn(Optional.of(notificationFor(id1, FIXED_NOW.minusSeconds(300))));
    when(notificationRepository.findById(id2))
        .thenReturn(Optional.of(notificationFor(id2, FIXED_NOW.minusSeconds(200))));
    when(templateRepository.findActiveByName("welcome")).thenReturn(Optional.of(welcomeTemplate()));
    when(templateRenderer.render(any(TemplateRenderRequest.class))).thenReturn("Hello Jane");
    SummarizedContent summary = new SummarizedContent("Update", "Two things happened.");
    when(summarizerPort.summarize(any(SummarizationRequest.class))).thenReturn(summary);
    org.mockito.Mockito.doThrow(new RuntimeException("db write failed"))
        .when(transactions).flushAggregate(any(), any(), any());

    int resolved = service().flushExpiredWindows();

    assertEquals(2, resolved);
    verify(transactions).flushIndividually(claimed1);
    verify(transactions).flushIndividually(claimed2);
  }

  /**
   * Per-notification render failure (Slice 2, task 2.16): one member of a
   * three-notification group fails to render (its underlying notification
   * cannot be found); that member alone falls back to individual delivery
   * while the other two still proceed to a successful aggregate.
   */
  @Test
  void flushExpiredWindows_oneMemberRenderFails_isolatedMemberIndividual_restAggregate() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    UUID id3 = UUID.randomUUID();
    BufferedNotification row1 = groupRow(id1, FIXED_NOW.minusSeconds(300));
    BufferedNotification row2 = groupRow(id2, FIXED_NOW.minusSeconds(200));
    BufferedNotification row3 = groupRow(id3, FIXED_NOW.minusSeconds(100));
    BufferedNotification claimed1 = row1.claim(FIXED_NOW);
    BufferedNotification claimed2 = row2.claim(FIXED_NOW);
    BufferedNotification claimed3 = row3.claim(FIXED_NOW);

    when(aggregationBufferRepository.findClaimable(any(), any()))
        .thenReturn(List.of(row1, row2, row3));
    when(transactions.claim(row1, FIXED_NOW)).thenReturn(Optional.of(claimed1));
    when(transactions.claim(row2, FIXED_NOW)).thenReturn(Optional.of(claimed2));
    when(transactions.claim(row3, FIXED_NOW)).thenReturn(Optional.of(claimed3));
    when(notificationRepository.findById(id1))
        .thenReturn(Optional.of(notificationFor(id1, FIXED_NOW.minusSeconds(300))));
    // id2's notification cannot be found — simulates a render-phase failure
    // isolated to this one member.
    when(notificationRepository.findById(id2)).thenReturn(Optional.empty());
    when(notificationRepository.findById(id3))
        .thenReturn(Optional.of(notificationFor(id3, FIXED_NOW.minusSeconds(100))));
    when(templateRepository.findActiveByName("welcome")).thenReturn(Optional.of(welcomeTemplate()));
    when(templateRenderer.render(any(TemplateRenderRequest.class))).thenReturn("Hello Jane");
    SummarizedContent summary = new SummarizedContent("Update", "Two things happened.");
    when(summarizerPort.summarize(any(SummarizationRequest.class))).thenReturn(summary);

    int resolved = service().flushExpiredWindows();

    assertEquals(3, resolved);
    verify(transactions).flushIndividually(claimed2);
    verify(transactions).flushAggregate(List.of(claimed1, claimed3), claimed1, summary);
    verify(transactions, never()).flushIndividually(claimed1);
    verify(transactions, never()).flushIndividually(claimed3);
  }
}
