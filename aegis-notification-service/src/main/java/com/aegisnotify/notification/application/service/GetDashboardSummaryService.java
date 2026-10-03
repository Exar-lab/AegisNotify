package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.DashboardAggregate;
import com.aegisnotify.notification.application.dto.DashboardSummaryResponse;
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
    Instant since = clock.instant().minus(window);
    DashboardAggregate aggregate = notificationRepository.aggregateSince(since);

    long successCount = aggregate.sentCount() + aggregate.sentViaFallbackCount();
    double successRatePercent = aggregate.totalCount() == 0
        ? 0.0
        : successCount * 100.0 / aggregate.totalCount();

    long avgDeliveryLatencyMillis = aggregate.avgTerminalLatencyMillis() == null
        ? 0L
        : Math.round(aggregate.avgTerminalLatencyMillis());

    return new DashboardSummaryResponse(
        aggregate.totalCount(),
        successRatePercent,
        aggregate.sentViaFallbackCount(),
        avgDeliveryLatencyMillis
    );
  }
}
