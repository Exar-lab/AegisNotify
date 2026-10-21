package com.aegisnotify.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.MetricsSummaryResponse;
import com.aegisnotify.notification.application.port.out.MetricsPort;
import com.aegisnotify.notification.application.service.GetMetricsSummaryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetMetricsSummaryServiceTest {

  @Mock
  private MetricsPort metricsPort;

  @Test
  void getSummary_delegatesToMetricsPort() {
    var snapshot = new MetricsSummaryResponse(
        List.of(), new MetricsSummaryResponse.DeliveryLatency(0, 0.0, 0.0), List.of(), 0,
        new MetricsSummaryResponse.KafkaConsumerCounts(0, 0, 0, 0));
    when(metricsPort.snapshot()).thenReturn(snapshot);

    assertThat(new GetMetricsSummaryService(metricsPort).getSummary()).isSameAs(snapshot);
  }
}
