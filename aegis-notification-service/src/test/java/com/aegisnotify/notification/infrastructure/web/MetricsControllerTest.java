package com.aegisnotify.notification.infrastructure.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegisnotify.notification.application.dto.MetricsSummaryResponse;
import com.aegisnotify.notification.application.port.in.GetMetricsSummaryUseCase;
import com.aegisnotify.notification.infrastructure.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MetricsController.class)
@Import(SecurityConfig.class)
class MetricsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private GetMetricsSummaryUseCase getMetricsSummaryUseCase;

  @Test
  void summary_authorized_returnsContractShape() throws Exception {
    when(getMetricsSummaryUseCase.getSummary()).thenReturn(new MetricsSummaryResponse(
        List.of(new MetricsSummaryResponse.ChannelRequestCount("EMAIL", 128)),
        new MetricsSummaryResponse.DeliveryLatency(452, 0.38, 2.1),
        List.of(new MetricsSummaryResponse.ProviderErrorCount("SendGrid", "500", 3)),
        12,
        new MetricsSummaryResponse.KafkaConsumerCounts(980, 4, 11, 1)));

    mockMvc.perform(get("/api/v1/metrics/summary")
            .with(jwt().authorities(() -> "SCOPE_notification:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestsByChannel[0].channel").value("EMAIL"))
        .andExpect(jsonPath("$.requestsByChannel[0].count").value(128))
        .andExpect(jsonPath("$.deliveryLatency.count").value(452))
        .andExpect(jsonPath("$.deliveryLatency.meanSeconds").value(0.38))
        .andExpect(jsonPath("$.deliveryLatency.maxSeconds").value(2.1))
        .andExpect(jsonPath("$.providerErrors[0].provider").value("SendGrid"))
        .andExpect(jsonPath("$.providerErrors[0].errorCode").value("500"))
        .andExpect(jsonPath("$.providerErrors[0].count").value(3))
        .andExpect(jsonPath("$.fallbackTransmissions").value(12))
        .andExpect(jsonPath("$.kafkaConsumer.success").value(980))
        .andExpect(jsonPath("$.kafkaConsumer.failure").value(4))
        .andExpect(jsonPath("$.kafkaConsumer.retry").value(11))
        .andExpect(jsonPath("$.kafkaConsumer.dlq").value(1));
  }

  @Test
  void summary_noToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/metrics/summary"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void summary_missingRequiredScope_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/metrics/summary")
            .with(jwt().authorities(() -> "SCOPE_notification:write")))
        .andExpect(status().isForbidden());
  }
}
