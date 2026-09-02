package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.application.dto.SummarizationRequest;
import com.aegisnotify.notification.application.dto.SummarizedContent;
import com.aegisnotify.notification.domain.exception.SummarizerUnavailableException;

/**
 * Outbound port for the aggregation/summarization agent (issue #86, Slice 2,
 * L1 of the design). Exactly one LLM-backed implementation exists in this
 * change ({@code AnthropicMessagesSummarizerAdapter}), plus a default
 * always-failing implementation for when aggregation is disabled or no API
 * key is configured ({@code UnavailableSummarizerAdapter}, L3) — the port
 * itself must never leak vendor/SDK types into application or domain code.
 */
public interface AggregationSummarizerPort {

  /**
   * Summarizes a group of already-rendered notification bodies into a single
   * aggregate subject/body pair.
   *
   * @param request rendered bodies only — no recipient, no raw parameter map,
   *                no notification ids (D4, enforced by {@link
   *                SummarizationRequest}'s shape)
   * @return the summarized subject/body; never {@code null}
   * @throws SummarizerUnavailableException on timeout, an open circuit
   *     breaker, an error response, or unusable/empty output. Never throws
   *     anything else.
   */
  SummarizedContent summarize(SummarizationRequest request);
}
