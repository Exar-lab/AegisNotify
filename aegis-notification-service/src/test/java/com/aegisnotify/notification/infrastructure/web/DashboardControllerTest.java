package com.aegisnotify.notification.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegisnotify.notification.application.dto.DashboardSummaryResponse;
import com.aegisnotify.notification.application.port.in.GetDashboardSummaryUseCase;
import com.aegisnotify.notification.infrastructure.config.SecurityConfig;
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

  @Test
  void summary_authorized_returns200() throws Exception {
    when(getDashboardSummaryUseCase.getSummary(any()))
        .thenReturn(new DashboardSummaryResponse(48392L, 99.12, 312L, 842L));

    mockMvc.perform(get("/api/v1/dashboard/summary")
            .with(jwt().authorities(() -> "SCOPE_notification:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRequests").value(48392))
        .andExpect(jsonPath("$.successRatePercent").value(99.12))
        .andExpect(jsonPath("$.fallbackActivations").value(312))
        .andExpect(jsonPath("$.avgDeliveryLatencyMillis").value(842));
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
}
