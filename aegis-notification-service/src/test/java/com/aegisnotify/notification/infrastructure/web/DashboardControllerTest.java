package com.aegisnotify.notification.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegisnotify.notification.application.dto.DashboardSummaryResponse;
import com.aegisnotify.notification.application.dto.MetricDelta;
import com.aegisnotify.notification.application.dto.ProviderCircuitBreakerStatus;
import com.aegisnotify.notification.application.port.in.GetDashboardSummaryUseCase;
import com.aegisnotify.notification.application.port.in.GetProviderCircuitBreakersUseCase;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.infrastructure.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private GetDashboardSummaryUseCase getDashboardSummaryUseCase;

  @MockitoBean
  private GetProviderCircuitBreakersUseCase getProviderCircuitBreakersUseCase;

  @Test
  void summary_authorized_returns200() throws Exception {
    when(getDashboardSummaryUseCase.getSummary(any()))
        .thenReturn(new DashboardSummaryResponse(
            new MetricDelta(48392.0, 42100.0, 14.94, null),
            new MetricDelta(99.12, 98.45, null, 0.67),
            new MetricDelta(312.0, 340.0, -8.24, null),
            new MetricDelta(842.0, 915.0, -7.98, null)
        ));

    mockMvc.perform(get("/api/v1/dashboard/summary")
            .with(jwt().authorities(() -> "SCOPE_notification:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRequests.currentValue").value(48392.0))
        .andExpect(jsonPath("$.totalRequests.previousValue").value(42100.0))
        .andExpect(jsonPath("$.totalRequests.deltaPercent").value(14.94))
        .andExpect(jsonPath("$.totalRequests.deltaPoints").doesNotExist())
        .andExpect(jsonPath("$.successRate.currentValue").value(99.12))
        .andExpect(jsonPath("$.successRate.previousValue").value(98.45))
        .andExpect(jsonPath("$.successRate.deltaPercent").doesNotExist())
        .andExpect(jsonPath("$.successRate.deltaPoints").value(0.67))
        .andExpect(jsonPath("$.fallbackActivations.currentValue").value(312.0))
        .andExpect(jsonPath("$.fallbackActivations.previousValue").value(340.0))
        .andExpect(jsonPath("$.fallbackActivations.deltaPercent").value(-8.24))
        .andExpect(jsonPath("$.avgDeliveryLatency.currentValue").value(842.0))
        .andExpect(jsonPath("$.avgDeliveryLatency.previousValue").value(915.0))
        .andExpect(jsonPath("$.avgDeliveryLatency.deltaPercent").value(-7.98));
  }

  @Test
  void summary_noToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/dashboard/summary"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void summary_missingRequiredScope_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/dashboard/summary")
            .with(jwt().authorities(() -> "SCOPE_notification:write")))
        .andExpect(status().isForbidden());
  }

  @Test
  void circuitBreakers_authorized_returns200() throws Exception {
    List<ProviderCircuitBreakerStatus> statuses = List.of(
        new ProviderCircuitBreakerStatus(
            "email-provider", "SendGrid", Channel.EMAIL, "PRIMARY",
            "SendGrid Secondary", "CLOSED", 5.0, 10.0,
            java.time.Instant.parse("2026-10-15T12:00:00Z"), 100L, 50L, 95.0
        )
    );
    when(getProviderCircuitBreakersUseCase.getCircuitBreakers())
        .thenReturn(statuses);

    mockMvc.perform(get("/api/v1/dashboard/circuit-breakers")
            .with(jwt().authorities(() -> "SCOPE_notification:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].key").value("email-provider"))
        .andExpect(jsonPath("$[0].name").value("SendGrid"))
        .andExpect(jsonPath("$[0].channel").value("EMAIL"))
        .andExpect(jsonPath("$[0].role").value("PRIMARY"))
        .andExpect(jsonPath("$[0].fallbackProvider").value("SendGrid Secondary"))
        .andExpect(jsonPath("$[0].state").value("CLOSED"))
        .andExpect(jsonPath("$[0].failureRate").value(5.0))
        .andExpect(jsonPath("$[0].slowCallRate").value(10.0))
        .andExpect(jsonPath("$[0].calls24h").value(100))
        .andExpect(jsonPath("$[0].callsToday").value(50))
        .andExpect(jsonPath("$[0].successRate").value(95.0))
        .andExpect(jsonPath("$[0].lastStateChange").value("2026-10-15T12:00:00Z"));
  }

  @Test
  void circuitBreakers_noToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/dashboard/circuit-breakers"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void circuitBreakers_missingRequiredScope_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/dashboard/circuit-breakers")
            .with(jwt().authorities(() -> "SCOPE_notification:write")))
        .andExpect(status().isForbidden());
  }
}
