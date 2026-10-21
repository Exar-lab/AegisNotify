package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.model.DeliveryAttempt;
import java.util.List;

public record ProviderResult(
    Outcome outcome,
    String providerName,
    String errorDetail,
    boolean retryable,
    String code,
    List<DeliveryAttempt> attempts
) {

  public ProviderResult {
    attempts = List.copyOf(attempts);
  }

  public ProviderResult(Outcome outcome, String providerName, String errorDetail,
      boolean retryable) {
    this(outcome, providerName, errorDetail, retryable, null, List.of());
  }

  /**
   * Convenience constructor for outcomes that carry no retry verdict —
   * successes, and failures whose cause is not classifiable as transient.
   * Defaults {@code retryable} to {@code false}, the safe default: a result
   * that does not explicitly claim transience is never retried.
   */
  public ProviderResult(Outcome outcome, String providerName, String errorDetail) {
    this(outcome, providerName, errorDetail, false);
  }

  public ProviderResult withCode(String resultCode) {
    return new ProviderResult(outcome, providerName, errorDetail, retryable, resultCode, attempts);
  }

  public ProviderResult withAttempts(List<DeliveryAttempt> deliveryAttempts) {
    return new ProviderResult(outcome, providerName, errorDetail, retryable, code,
        deliveryAttempts);
  }

  public enum Outcome {
    SENT,
    SENT_VIA_FALLBACK,
    FAILED,
    FAILED_CRITICAL
  }
}
