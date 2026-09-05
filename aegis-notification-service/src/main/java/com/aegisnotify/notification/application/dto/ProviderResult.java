package com.aegisnotify.notification.application.dto;

public record ProviderResult(
    Outcome outcome,
    String providerName,
    String errorDetail,
    boolean retryable
) {

  /**
   * Convenience constructor for outcomes that carry no retry verdict —
   * successes, and failures whose cause is not classifiable as transient.
   * Defaults {@code retryable} to {@code false}, the safe default: a result
   * that does not explicitly claim transience is never retried.
   */
  public ProviderResult(Outcome outcome, String providerName, String errorDetail) {
    this(outcome, providerName, errorDetail, false);
  }

  public enum Outcome {
    SENT,
    SENT_VIA_FALLBACK,
    FAILED,
    FAILED_CRITICAL
  }
}
