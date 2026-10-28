package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.DashboardAggregate;
import com.aegisnotify.notification.application.dto.DashboardSummaryResponse;
import com.aegisnotify.notification.application.port.out.NotificationRepository;
import com.aegisnotify.notification.application.service.GetDashboardSummaryService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetDashboardSummaryServiceTest {

  @Mock
  private NotificationRepository notificationRepository;

  @Test
  void getSummary_mixedOutcomes_computesSuccessRateAndDeltas() {
    Instant fixedNow = Instant.parse("2026-01-02T12:00:00Z");
    Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    GetDashboardSummaryService service =
        new GetDashboardSummaryService(notificationRepository, clock);

    Instant currentStart = fixedNow.minus(Duration.ofHours(24));
    Instant previousStart = currentStart.minus(Duration.ofHours(24));

    // current: 10 total, 7 sent, 1 fallback, 1 failed, 1 failed_critical (terminal=10, 80.0%)
    when(notificationRepository.aggregateBetween(currentStart, fixedNow))
        .thenReturn(new DashboardAggregate(10L, 7L, 1L, 1L, 1L, 842.0));

    // previous: 8 total, 5 sent, 1 fallback, 2 failed, 0 failed_critical (terminal=8, 75.0%)
    when(notificationRepository.aggregateBetween(previousStart, currentStart))
        .thenReturn(new DashboardAggregate(8L, 5L, 1L, 2L, 0L, 900.0));

    DashboardSummaryResponse result = service.getSummary(Duration.ofHours(24));

    // totalRequests: 10 vs 8 -> +25.0%
    assertEquals(10.0, result.totalRequests().currentValue());
    assertEquals(8.0, result.totalRequests().previousValue());
    assertEquals(25.0, result.totalRequests().deltaPercent());
    assertNull(result.totalRequests().deltaPoints());

    // successRate: 80.0 vs 75.0 -> +5.0 points
    assertEquals(80.0, result.successRate().currentValue());
    assertEquals(75.0, result.successRate().previousValue());
    assertNull(result.successRate().deltaPercent());
    assertEquals(5.0, result.successRate().deltaPoints(), 0.001);

    // fallbackActivations: 1 vs 1 -> 0.0%
    assertEquals(1.0, result.fallbackActivations().currentValue());
    assertEquals(1.0, result.fallbackActivations().previousValue());
    assertEquals(0.0, result.fallbackActivations().deltaPercent());

    // avgDeliveryLatency: 842 vs 900 -> ((842 - 900) / 900) * 100 = -6.444%
    assertEquals(842.0, result.avgDeliveryLatency().currentValue());
    assertEquals(900.0, result.avgDeliveryLatency().previousValue());
    double expectedLatencyDelta = ((842.0 - 900.0) / 900.0) * 100.0;
    assertEquals(expectedLatencyDelta, result.avgDeliveryLatency().deltaPercent(), 0.001);
  }

  @Test
  void getSummary_noActivity_returnsZeroRatesAndNullDeltas() {
    Instant fixedNow = Instant.parse("2026-01-02T12:00:00Z");
    Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    GetDashboardSummaryService service =
        new GetDashboardSummaryService(notificationRepository, clock);

    Instant currentStart = fixedNow.minus(Duration.ofHours(24));
    Instant previousStart = currentStart.minus(Duration.ofHours(24));

    when(notificationRepository.aggregateBetween(currentStart, fixedNow))
        .thenReturn(new DashboardAggregate(0L, 0L, 0L, 0L, 0L, null));
    when(notificationRepository.aggregateBetween(previousStart, currentStart))
        .thenReturn(new DashboardAggregate(0L, 0L, 0L, 0L, 0L, null));

    DashboardSummaryResponse result = service.getSummary(Duration.ofHours(24));

    assertEquals(0.0, result.totalRequests().currentValue());
    assertNull(result.totalRequests().deltaPercent());

    assertEquals(0.0, result.successRate().currentValue());
    assertEquals(0.0, result.successRate().deltaPoints());

    assertEquals(0.0, result.fallbackActivations().currentValue());
    assertNull(result.fallbackActivations().deltaPercent());

    assertEquals(0.0, result.avgDeliveryLatency().currentValue());
    assertNull(result.avgDeliveryLatency().deltaPercent());
  }

  @Test
  void getSummary_queriesBothWindowRanges() {
    Instant fixedNow = Instant.parse("2026-01-02T12:00:00Z");
    Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    GetDashboardSummaryService service =
        new GetDashboardSummaryService(notificationRepository, clock);

    Instant currentStart = fixedNow.minus(Duration.ofHours(6));
    Instant previousStart = currentStart.minus(Duration.ofHours(6));

    when(notificationRepository.aggregateBetween(currentStart, fixedNow))
        .thenReturn(new DashboardAggregate(0L, 0L, 0L, 0L, 0L, null));
    when(notificationRepository.aggregateBetween(previousStart, currentStart))
        .thenReturn(new DashboardAggregate(0L, 0L, 0L, 0L, 0L, null));

    service.getSummary(Duration.ofHours(6));

    verify(notificationRepository).aggregateBetween(currentStart, fixedNow);
    verify(notificationRepository).aggregateBetween(previousStart, currentStart);
  }
}
