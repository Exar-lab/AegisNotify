package com.aegisnotify.notification.infrastructure.provider;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Records the two provider-call metrics shared by every channel adapter:
 * delivery latency (regardless of outcome) and provider error counts.
 */
class ProviderMetrics {

  private static final String METER_LATENCY = "aegisnotify.delivery.latency.seconds";
  private static final String METER_ERROR_COUNT = "aegisnotify.provider.error.count";

  private final MeterRegistry meterRegistry;

  ProviderMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  Timer.Sample startTimer() {
    return Timer.start(meterRegistry);
  }

  void stopTimer(Timer.Sample sample, String providerName) {
    sample.stop(meterRegistry.timer(METER_LATENCY, "provider", providerName));
  }

  void recordError(String providerName, String errorCode) {
    meterRegistry.counter(METER_ERROR_COUNT, "provider", providerName, "error_code", errorCode)
        .increment();
  }
}
