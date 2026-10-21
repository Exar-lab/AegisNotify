package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.ProviderCallMetrics;
import com.aegisnotify.notification.application.dto.ProviderCircuitBreakerStatus;
import com.aegisnotify.notification.application.port.out.CircuitBreakerInspectionPort;
import com.aegisnotify.notification.application.port.out.ProviderCallMetricsPort;
import com.aegisnotify.notification.application.service.GetProviderCircuitBreakersService;
import com.aegisnotify.notification.domain.enums.Channel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetProviderCircuitBreakersServiceTest {

  @Mock
  private CircuitBreakerInspectionPort circuitBreakerInspectionPort;

  @Mock
  private ProviderCallMetricsPort metricsPort;

  @Test
  void getCircuitBreakers_delegatesToInspectionPort() {
    Instant now = Instant.parse("2026-10-15T12:00:00Z");
    var service = new GetProviderCircuitBreakersService(circuitBreakerInspectionPort,
        metricsPort, Clock.fixed(now, ZoneOffset.UTC));
    List<ProviderCircuitBreakerStatus> expected = List.of(
        new ProviderCircuitBreakerStatus(
            "email-provider", "SendGrid", Channel.EMAIL, "PRIMARY",
            "SendGrid Secondary", "CLOSED"
        )
    );
    when(circuitBreakerInspectionPort.getProviderCircuitBreakerStatuses())
        .thenReturn(expected);
    when(metricsPort.aggregate(now.minusSeconds(86400),
        Instant.parse("2026-10-15T00:00:00Z"), now))
        .thenReturn(List.of(new ProviderCallMetrics(Channel.EMAIL, "PRIMARY", 20, 10, 19)));

    List<ProviderCircuitBreakerStatus> actual = service.getCircuitBreakers();

    verify(circuitBreakerInspectionPort).getProviderCircuitBreakerStatuses();
    assertEquals(20L, actual.getFirst().calls24h());
    assertEquals(10L, actual.getFirst().callsToday());
    assertEquals(95.0, actual.getFirst().successRate());
    assertEquals("CLOSED", actual.getFirst().state());
  }

  @Test
  void providerWithoutCallsHasNoSuccessRateAndDoesNotReuseAnotherRolesMetrics() {
    Instant now = Instant.parse("2026-10-15T00:00:00Z");
    var service = new GetProviderCircuitBreakersService(circuitBreakerInspectionPort,
        metricsPort, Clock.fixed(now, ZoneOffset.UTC));
    when(circuitBreakerInspectionPort.getProviderCircuitBreakerStatuses()).thenReturn(List.of(
        new ProviderCircuitBreakerStatus("email-fallback", "Secondary", Channel.EMAIL,
            "FALLBACK", null, "NOT_PROTECTED")));
    when(metricsPort.aggregate(now.minusSeconds(86400), now, now)).thenReturn(List.of(
        new ProviderCallMetrics(Channel.EMAIL, "PRIMARY", 20, 10, 19)));
    var provider = service.getCircuitBreakers().getFirst();
    assertEquals(0L, provider.calls24h());
    assertEquals(0L, provider.callsToday());
    assertEquals(null, provider.successRate());
  }
}

