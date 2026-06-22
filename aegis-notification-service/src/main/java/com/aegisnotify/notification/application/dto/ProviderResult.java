package com.aegisnotify.notification.application.dto;

public record ProviderResult(
    Outcome outcome,
    String providerName,
    String errorDetail
) {

  public enum Outcome {
    SENT,
    SENT_VIA_FALLBACK,
    FAILED,
    FAILED_CRITICAL
  }
}
