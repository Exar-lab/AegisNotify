package com.aegisnotify.notification.domain.exception;

/**
 * Thrown when the aggregation summarizer cannot produce a summary — timeout,
 * open circuit breaker, error response, or unusable output.
 *
 * <p>Mirrors {@link TemplateRenderingException}'s placement: thrown by an
 * infrastructure adapter ({@code AggregationSummarizerPort} implementation),
 * caught by application-layer orchestration ({@code
 * FlushAggregationWindowsService}), whose only reaction is to release the
 * buffered group to individual delivery. Slice 1 does not yet have a real
 * summarizer implementation, but the exception type must exist because the
 * flush-with-fallback-to-individual-delivery contract is defined now.</p>
 */
public final class SummarizerUnavailableException extends DomainException {

  public SummarizerUnavailableException(String message) {
    super(message);
  }

  public SummarizerUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
