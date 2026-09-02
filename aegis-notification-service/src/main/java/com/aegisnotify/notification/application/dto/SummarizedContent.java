package com.aegisnotify.notification.application.dto;

/**
 * Output of {@link com.aegisnotify.notification.application.port.out
 * .AggregationSummarizerPort#summarize}. {@code body} is what gets persisted
 * as the leader notification's {@code aggregate_body} (X2) — already
 * escaped for {@code Channel.EMAIL} and length-capped by the adapter (L4),
 * so callers must treat it as ready to store/deliver verbatim.
 *
 * @param subject a short subject line for the aggregate; advisory only —
 *                the current schema has no dedicated aggregate-subject
 *                column, so callers may fall back to the group's template
 *                subject instead of persisting this value
 * @param body    the summarized body, ready to store as {@code
 *                aggregate_body}
 */
public record SummarizedContent(String subject, String body) {
}
