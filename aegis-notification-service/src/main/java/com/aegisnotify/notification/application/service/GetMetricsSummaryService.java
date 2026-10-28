package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.MetricsSummaryResponse;
import com.aegisnotify.notification.application.port.in.GetMetricsSummaryUseCase;
import com.aegisnotify.notification.application.port.out.MetricsPort;
import org.springframework.stereotype.Service;

@Service
public class GetMetricsSummaryService implements GetMetricsSummaryUseCase {

  private final MetricsPort metricsPort;

  public GetMetricsSummaryService(MetricsPort metricsPort) {
    this.metricsPort = metricsPort;
  }

  @Override
  public MetricsSummaryResponse getSummary() {
    return metricsPort.snapshot();
  }
}
