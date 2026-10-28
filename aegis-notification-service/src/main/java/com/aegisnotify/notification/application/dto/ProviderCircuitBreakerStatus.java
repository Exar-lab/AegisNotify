package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.enums.Channel;
import java.time.Instant;

public record ProviderCircuitBreakerStatus(
    String key,
    String name,
    Channel channel,
    String role,
    String fallbackProvider,
    String state,
    Double failureRate,
    Double slowCallRate,
    Instant lastStateChange,
    Long calls24h,
    Long callsToday,
    Double successRate
) {
  public ProviderCircuitBreakerStatus(String key, String name, Channel channel, String role,
      String fallbackProvider, String state) {
    this(key, name, channel, role, fallbackProvider, state, null, null, null, null, null, null);
  }

  public ProviderCircuitBreakerStatus withMetrics(ProviderCallMetrics metrics) {
    return new ProviderCircuitBreakerStatus(key, name, channel, role, fallbackProvider, state,
        failureRate, slowCallRate, lastStateChange, metrics.calls(), metrics.callsToday(),
        metrics.calls() == 0 ? null : metrics.successes() * 100.0 / metrics.calls());
  }
}
