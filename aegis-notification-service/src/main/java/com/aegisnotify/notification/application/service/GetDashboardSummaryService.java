package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.DashboardAggregate;
import com.aegisnotify.notification.application.dto.DashboardSummaryResponse;
import com.aegisnotify.notification.application.dto.MetricDelta;
import com.aegisnotify.notification.application.port.in.GetDashboardSummaryUseCase;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class GetDashboardSummaryService implements GetDashboardSummaryUseCase {

  private final NotificationRepository notificationRepository;
  private final Clock clock;

  public GetDashboardSummaryService(NotificationRepository notificationRepository, Clock clock) {
    this.notificationRepository = notificationRepository;
    this.clock = clock;
  }

  @Override
  public DashboardSummaryResponse getSummary(Duration window) {
    Instant now = clock.instant();
    Instant currentStart = now.minus(window);
    Instant previousStart = currentStart.minus(window);

    DashboardAggregate current = notificationRepository.aggregateBetween(currentStart, now);
    DashboardAggregate previous =
        notificationRepository.aggregateBetween(previousStart, currentStart);

    double currentTotal = current.totalCount();
    double previousTotal = previous.totalCount();
    MetricDelta totalRequests = new MetricDelta(
        currentTotal,
        previousTotal,
        calculateDeltaPercent(currentTotal, previousTotal),
        null
    );

    double currentSuccessRate = calculateSuccessRate(current);
    double previousSuccessRate = calculateSuccessRate(previous);
    MetricDelta successRate = new MetricDelta(
        currentSuccessRate,
        previousSuccessRate,
        null,
        currentSuccessRate - previousSuccessRate
    );

    double currentFallback = current.sentViaFallbackCount();
    double previousFallback = previous.sentViaFallbackCount();
    MetricDelta fallbackActivations = new MetricDelta(
        currentFallback,
        previousFallback,
        calculateDeltaPercent(currentFallback, previousFallback),
        null
    );

    double currentLatency = current.avgTerminalLatencyMillis() != null
        ? current.avgTerminalLatencyMillis() : 0.0;
    double previousLatency = previous.avgTerminalLatencyMillis() != null
        ? previous.avgTerminalLatencyMillis() : 0.0;
    MetricDelta avgDeliveryLatency = new MetricDelta(
        currentLatency,
        previousLatency,
        calculateDeltaPercent(currentLatency, previousLatency),
        null
    );

    return new DashboardSummaryResponse(
        totalRequests,
        successRate,
        fallbackActivations,
        avgDeliveryLatency
    );
  }

  private double calculateSuccessRate(DashboardAggregate aggregate) {
    long terminalCount = aggregate.sentCount()
        + aggregate.sentViaFallbackCount()
        + aggregate.failedCount()
        + aggregate.failedCriticalCount();

    if (terminalCount == 0) {
      return 0.0;
    }

    long successCount = aggregate.sentCount() + aggregate.sentViaFallbackCount();
    return successCount * 100.0 / terminalCount;
  }

  private Double calculateDeltaPercent(double current, double previous) {
    if (previous == 0.0) {
      return null;
    }
    return ((current - previous) / previous) * 100.0;
  }
}
