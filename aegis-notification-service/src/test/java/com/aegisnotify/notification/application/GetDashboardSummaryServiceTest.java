package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
  void getSummary_mixedOutcomes_computesSuccessRateAndLatency() {
    Instant fixedNow = Instant.parse("2026-01-01T12:00:00Z");
    Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    GetDashboardSummaryService service =
        new GetDashboardSummaryService(notificationRepository, clock);

    when(notificationRepository.aggregateSince(fixedNow.minus(Duration.ofHours(24))))
        .thenReturn(new DashboardAggregate(10L, 7L, 1L, 2L, 842.5));

    DashboardSummaryResponse result = service.getSummary(Duration.ofHours(24));

    assertEquals(10L, result.totalRequests());
    assertEquals(80.0, result.successRatePercent());
    assertEquals(1L, result.fallbackActivations());
    assertEquals(843L, result.avgDeliveryLatencyMillis());
  }

  @Test
  void getSummary_noActivity_returnsZeroRateNotFailure() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    GetDashboardSummaryService service =
        new GetDashboardSummaryService(notificationRepository, clock);

    when(notificationRepository.aggregateSince(any()))
        .thenReturn(new DashboardAggregate(0L, 0L, 0L, 0L, null));

    DashboardSummaryResponse result = service.getSummary(Duration.ofHours(24));

    assertEquals(0L, result.totalRequests());
    assertEquals(0.0, result.successRatePercent());
    assertEquals(0L, result.fallbackActivations());
    assertEquals(0L, result.avgDeliveryLatencyMillis());
  }

  @Test
  void getSummary_passesWindowStartToRepository() {
    Instant fixedNow = Instant.parse("2026-01-01T12:00:00Z");
    Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    GetDashboardSummaryService service =
        new GetDashboardSummaryService(notificationRepository, clock);
    when(notificationRepository.aggregateSince(any()))
        .thenReturn(new DashboardAggregate(0L, 0L, 0L, 0L, null));

    service.getSummary(Duration.ofHours(6));

    verify(notificationRepository).aggregateSince(fixedNow.minus(Duration.ofHours(6)));
  }
}
