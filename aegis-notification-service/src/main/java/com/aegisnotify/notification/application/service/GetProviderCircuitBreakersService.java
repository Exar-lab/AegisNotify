package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.ProviderCallMetrics;
import com.aegisnotify.notification.application.dto.ProviderCircuitBreakerStatus;
import com.aegisnotify.notification.application.port.in.GetProviderCircuitBreakersUseCase;
import com.aegisnotify.notification.application.port.out.CircuitBreakerInspectionPort;
import com.aegisnotify.notification.application.port.out.ProviderCallMetricsPort;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GetProviderCircuitBreakersService implements GetProviderCircuitBreakersUseCase {

  private final CircuitBreakerInspectionPort circuitBreakerInspectionPort;
  private final ProviderCallMetricsPort metricsPort;
  private final Clock clock;

  public GetProviderCircuitBreakersService(
      CircuitBreakerInspectionPort circuitBreakerInspectionPort,
      ProviderCallMetricsPort metricsPort, Clock clock) {
    this.circuitBreakerInspectionPort = circuitBreakerInspectionPort;
    this.metricsPort = metricsPort;
    this.clock = clock;
  }

  @Override
  public List<ProviderCircuitBreakerStatus> getCircuitBreakers() {
    var now = clock.instant();
    var today = now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    var metrics = metricsPort.aggregate(now.minus(Duration.ofHours(24)), today, now);
    return circuitBreakerInspectionPort.getProviderCircuitBreakerStatuses().stream()
        .map(provider -> provider.withMetrics(metrics.stream()
            .filter(metric -> metric.channel() == provider.channel()
                && metric.role().equals(provider.role()))
            .findFirst().orElse(new ProviderCallMetrics(
                provider.channel(), provider.role(), 0, 0, 0))))
        .toList();
  }
}
