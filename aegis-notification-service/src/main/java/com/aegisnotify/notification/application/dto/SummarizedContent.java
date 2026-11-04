package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.enums.Priority;

/**
 * Output of {@link com.aegisnotify.notification.application.port.out
 * .AggregationSummarizerPort#summarize}. {@code body} is what gets persisted
 * as the leader notification's {@code aggregate_body} (X2) — already
 * escaped for {@code Channel.EMAIL} and length-capped by the adapter (L4),
 * so callers must treat it as ready to store/deliver verbatim.
 *
 * @param subject  a short subject line for the aggregate; stored
 *                 as the leader's {@code aggregate_subject} and used as the
 *                 delivered subject; may be {@code null} (the adapter returns
 *                 {@code null} for a blank subject), in which case delivery
 *                 falls back to the leader's template subject
 * @param body     the summarized body, ready to store as {@code
 *                 aggregate_body}
 * @param priority the priority the model suggested for the aggregate (issue
 *                 #227), already validated against {@link Priority}; {@code
 *                 null} when it was not requested, absent or invalid. It is a
 *                 suggestion only — the flush decides what is applied
 */
public record SummarizedContent(String subject, String body, Priority priority) {

  /** Summary without a model-suggested priority. */
  public SummarizedContent(String subject, String body) {
    this(subject, body, null);
  }
}
