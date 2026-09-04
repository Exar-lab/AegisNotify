package com.aegisnotify.notification.infrastructure.summarizer;

import com.aegisnotify.notification.application.dto.SummarizationRequest;
import com.aegisnotify.notification.application.dto.SummarizedContent;
import com.aegisnotify.notification.application.port.out.AggregationSummarizerPort;
import com.aegisnotify.notification.domain.exception.SummarizerUnavailableException;

/**
 * Safe default {@link AggregationSummarizerPort} wired by {@code
 * AggregationConfig} when aggregation is disabled or no summarizer API key
 * is configured (L3 of the design). Mirrors {@code
 * ProviderConfig.NO_SECONDARY_PROVIDER}'s idiom: a missing optional
 * dependency degrades to a safe, always-fails outcome instead of blocking
 * application startup. Every call always throws {@link
 * SummarizerUnavailableException}, which is the exact signal {@code
 * FlushAggregationWindowsService} needs to fall back a whole group to
 * individual delivery — this class alone guarantees "no summarizer
 * configured" never becomes "notifications dropped".
 */
public final class UnavailableSummarizerAdapter implements AggregationSummarizerPort {

  @Override
  public SummarizedContent summarize(SummarizationRequest request) {
    throw new SummarizerUnavailableException(
        "No aggregation summarizer configured (aggregation disabled or no API key)");
  }
}
