package com.aegisnotify.notification.infrastructure.web;

import com.aegisnotify.notification.application.dto.MetricsSummaryResponse;
import com.aegisnotify.notification.application.port.in.GetMetricsSummaryUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

  private final GetMetricsSummaryUseCase getMetricsSummaryUseCase;

  public MetricsController(GetMetricsSummaryUseCase getMetricsSummaryUseCase) {
    this.getMetricsSummaryUseCase = getMetricsSummaryUseCase;
  }

  @GetMapping("/summary")
  public ResponseEntity<MetricsSummaryResponse> summary() {
    return ResponseEntity.ok(getMetricsSummaryUseCase.getSummary());
  }
}
