package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.SummarizationRequest;
import com.aegisnotify.notification.application.dto.SummarizedContent;
import com.aegisnotify.notification.application.dto.TemplateRenderRequest;
import com.aegisnotify.notification.application.port.in.FlushAggregationWindowsUseCase;
import com.aegisnotify.notification.application.port.out.AggregationBufferRepository;
import com.aegisnotify.notification.application.port.out.AggregationSummarizerPort;
import com.aegisnotify.notification.application.port.out.NotificationMetricsPort;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.port.out.TemplateRenderer;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.exception.SummarizerUnavailableException;
import com.aegisnotify.notification.domain.exception.TemplateNotFoundException;
import com.aegisnotify.notification.domain.model.AggregationGroupKey;
import com.aegisnotify.notification.domain.model.AggregationSettings;
import com.aegisnotify.notification.domain.model.BufferedNotification;
import com.aegisnotify.notification.domain.model.Notification;
import com.aegisnotify.notification.domain.model.Template;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a single aggregation-flush poll without holding one database
 * transaction open across the whole batch — the claim and resolve work for
 * each buffered row runs in its own transaction, owned by {@link
 * AggregationFlushTransactions}.
 *
 * <p>Slice 2 wires in the summarizer: claimable rows are first grouped by
 * {@link BufferedNotification#groupKey}. A group of exactly one row has
 * nothing to aggregate with, so it takes Slice 1's unchanged individual path
 * with zero summarizer calls — summarizing a lone message would waste an LLM
 * call for no benefit and is not what "aggregation" means. A group of two or
 * more attempts summarization: each member's body is rendered (D4a — render
 * happens at flush time, not hold time), the summarizer is called with NO
 * transaction open (B3), and on success exactly one aggregate outbox event
 * is written via {@link AggregationFlushTransactions#flushAggregate}. ANY
 * failure — a per-member render failure, {@link
 * SummarizerUnavailableException} (timeout, open breaker, error response,
 * unusable output), or a failure persisting the aggregate itself — falls
 * that/those member(s) back to Slice 1's individual-delivery path. Nothing
 * is ever dropped.</p>
 *
 * <p>{@link Clock} is injected (not {@code Instant.now()}) so window-expiry
 * boundary behavior is deterministically testable.</p>
 */
@Service
public class FlushAggregationWindowsService implements FlushAggregationWindowsUseCase {

  private static final Logger log = LoggerFactory.getLogger(FlushAggregationWindowsService.class);

  /**
   * Soft hint passed to the summarizer's prompt (see {@link
   * SummarizationRequest#maxLength()}). Deliberately NOT a new config key:
   * the actually enforced cap on stored output is {@code
   * SummarizerProperties#maxOutputChars()}, applied by the adapter itself
   * (L4) — this constant only shapes what the LLM is asked to aim for.
   */
  private static final int SUMMARY_MAX_LENGTH_HINT = 480;

  private final AggregationBufferRepository aggregationBufferRepository;
  private final AggregationFlushTransactions transactions;
  private final AggregationSettings settings;
  private final Clock clock;
  private final NotificationMetricsPort metrics;
  private final NotificationRepository notificationRepository;
  private final TemplateRepository templateRepository;
  private final TemplateRenderer templateRenderer;
  private final AggregationSummarizerPort summarizerPort;

  public FlushAggregationWindowsService(AggregationBufferRepository aggregationBufferRepository,
      AggregationFlushTransactions transactions, AggregationSettings settings, Clock clock,
      NotificationMetricsPort metrics, NotificationRepository notificationRepository,
      TemplateRepository templateRepository, TemplateRenderer templateRenderer,
      AggregationSummarizerPort summarizerPort) {
    this.aggregationBufferRepository = aggregationBufferRepository;
    this.transactions = transactions;
    this.settings = settings;
    this.clock = clock;
    this.metrics = metrics;
    this.notificationRepository = notificationRepository;
    this.templateRepository = templateRepository;
    this.templateRenderer = templateRenderer;
    this.summarizerPort = summarizerPort;
  }

  @Override
  public int flushExpiredWindows() {
    Instant now = clock.instant();
    Instant leaseCutoff = now.minus(settings.lease());

    List<BufferedNotification> claimable =
        aggregationBufferRepository.findClaimable(now, leaseCutoff);

    Map<AggregationGroupKey, List<BufferedNotification>> groups = claimable.stream()
        .collect(Collectors.groupingBy(
            row -> row.groupKey(settings.requireSameTemplate()),
            LinkedHashMap::new, Collectors.toList()));

    int resolvedCount = 0;
    for (List<BufferedNotification> groupRows : groups.values()) {
      resolvedCount += groupRows.size() == 1
          ? flushSingle(groupRows.get(0), now)
          : flushGroup(groupRows, now);
    }
    return resolvedCount;
  }

  private int flushSingle(BufferedNotification bufferedNotification, Instant now) {
    Optional<BufferedNotification> claimed;
    try {
      claimed = transactions.claim(bufferedNotification, now);
    } catch (RuntimeException ex) {
      log.warn("aggregation_buffer_claim_failed bufferedNotificationId={} reason={}",
          bufferedNotification.getId(), ex.getMessage());
      metrics.recordAggregationFlushError("claim_failed");
      return 0;
    }

    if (claimed.isEmpty()) {
      // Lost the single-claimant race to a concurrent poll — skip, the
      // winner is responsible for resolving this row.
      return 0;
    }

    return resolveClaimedRow(claimed.get()) ? 1 : 0;
  }

  private int flushGroup(List<BufferedNotification> groupRows, Instant now) {
    List<BufferedNotification> claimed = new ArrayList<>();
    for (BufferedNotification row : groupRows) {
      try {
        transactions.claim(row, now).ifPresent(claimed::add);
      } catch (RuntimeException ex) {
        log.warn("aggregation_buffer_claim_failed bufferedNotificationId={} reason={}",
            row.getId(), ex.getMessage());
        metrics.recordAggregationFlushError("claim_failed");
      }
    }

    if (claimed.isEmpty()) {
      return 0;
    }
    if (claimed.size() == 1) {
      // Every other member lost the claim race (or failed to claim) —
      // nothing left to aggregate with, fall back to the single-row path.
      return resolveClaimedRow(claimed.get(0)) ? 1 : 0;
    }

    int resolvedCount = 0;
    List<RenderedMember> rendered = new ArrayList<>();
    for (BufferedNotification row : claimed) {
      try {
        rendered.add(renderMember(row));
      } catch (RuntimeException ex) {
        log.warn("aggregation_member_render_failed bufferedNotificationId={} reason={}",
            row.getId(), ex.getMessage());
        metrics.recordAggregationFlushError("render_failed");
        if (resolveClaimedRow(row)) {
          resolvedCount++;
        }
      }
    }

    if (rendered.size() < 2) {
      // Fewer than two renderable members remain: nothing left worth
      // summarizing, release whatever is left to individual delivery.
      for (RenderedMember member : rendered) {
        if (resolveClaimedRow(member.row())) {
          resolvedCount++;
        }
      }
      return resolvedCount;
    }

    List<BufferedNotification> memberRows = rendered.stream().map(RenderedMember::row).toList();
    Channel channel = memberRows.get(0).getChannel();
    String templateName = memberRows.get(0).getTemplateName();
    List<String> bodies = rendered.stream().map(RenderedMember::body).toList();

    SummarizedContent summary;
    try {
      summary = summarizerPort.summarize(
          new SummarizationRequest(channel, templateName, bodies, SUMMARY_MAX_LENGTH_HINT));
    } catch (SummarizerUnavailableException ex) {
      log.warn("aggregation_summarizer_unavailable groupSize={} reason={}",
          memberRows.size(), ex.getMessage());
      metrics.recordAggregationFlushError("summarizer_unavailable");
      for (RenderedMember member : rendered) {
        if (resolveClaimedRow(member.row())) {
          resolvedCount++;
        }
      }
      return resolvedCount;
    }

    BufferedNotification leaderRow = rendered.stream()
        .min(Comparator.comparing(RenderedMember::notificationCreatedAt))
        .map(RenderedMember::row)
        .orElseThrow();

    try {
      transactions.flushAggregate(memberRows, leaderRow, summary);
      metrics.recordAggregationFlushSuccess();
      resolvedCount += memberRows.size();
    } catch (RuntimeException ex) {
      log.warn("aggregation_group_flush_failed groupSize={} reason={}",
          memberRows.size(), ex.getMessage());
      metrics.recordAggregationFlushError("flush_failed");
      for (BufferedNotification row : memberRows) {
        if (resolveClaimedRow(row)) {
          resolvedCount++;
        }
      }
    }
    return resolvedCount;
  }

  private RenderedMember renderMember(BufferedNotification row) {
    Notification notification = notificationRepository.findById(row.getNotificationId())
        .orElseThrow(() -> new IllegalStateException(
            "Notification not found for buffered row: " + row.getNotificationId()));
    Template template = templateRepository.findActiveByName(notification.getTemplateName())
        .orElseThrow(() -> new TemplateNotFoundException(notification.getTemplateName()));

    TemplateRenderRequest request = new TemplateRenderRequest(
        template.getBody(), notification.getParameters(), template.getVariables(),
        template.getChannel());
    String body = templateRenderer.render(request);
    return new RenderedMember(row, body, notification.getCreatedAt());
  }

  /**
   * A claimed row that failed to render, or that lost the summarizer step,
   * is resolved via Slice 1's individual-delivery path (poison-guard aware).
   * Same shared resolution helper used by both {@link #flushSingle} and
   * every fallback branch of {@link #flushGroup} — a failure resolving one
   * row must never abort the rest of the batch.
   */
  private boolean resolveClaimedRow(BufferedNotification claimed) {
    try {
      transactions.flushIndividually(claimed);
      metrics.recordAggregationFlushSuccess();
      return true;
    } catch (RuntimeException ex) {
      log.warn("aggregation_buffer_flush_failed bufferedNotificationId={} attempts={} reason={}",
          claimed.getId(), claimed.getAttempts(), ex.getMessage());
      metrics.recordAggregationFlushError("flush_failed");

      if (claimed.hasExceededMaxAttempts(settings.maxAttempts())) {
        try {
          transactions.forcePoisonRowDone(claimed, ex.getMessage());
        } catch (RuntimeException forceEx) {
          log.warn("aggregation_buffer_force_poison_row_failed bufferedNotificationId={} "
              + "reason={}", claimed.getId(), forceEx.getMessage());
          metrics.recordAggregationFlushError("force_poison_row_failed");
        }
      }
      // Left CLAIMED when still under maxAttempts: reclaimable again once
      // its lease expires, giving the next poll another attempt.
      return false;
    }
  }

  private record RenderedMember(BufferedNotification row, String body,
      Instant notificationCreatedAt) {
  }
}
