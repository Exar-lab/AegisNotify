package com.aegisnotify.notification.infrastructure.web;

import com.aegisnotify.notification.application.dto.DashboardSummaryResponse;
import com.aegisnotify.notification.application.port.in.GetDashboardSummaryUseCase;
import java.time.Duration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

  private static final Duration SUMMARY_WINDOW = Duration.ofHours(24);

  private final GetDashboardSummaryUseCase getDashboardSummaryUseCase;

  public DashboardController(GetDashboardSummaryUseCase getDashboardSummaryUseCase) {
    this.getDashboardSummaryUseCase = getDashboardSummaryUseCase;
  }

  @GetMapping("/summary")
  public ResponseEntity<DashboardSummaryResponse> summary() {
    var response = getDashboardSummaryUseCase.getSummary(SUMMARY_WINDOW);
    return ResponseEntity.ok(response);
  }
}
