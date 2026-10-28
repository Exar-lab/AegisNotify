package com.aegisnotify.notification.infrastructure.web;

import com.aegisnotify.notification.application.dto.DashboardSummaryResponse;
import com.aegisnotify.notification.application.dto.ProviderCircuitBreakerStatus;
import com.aegisnotify.notification.application.port.in.GetDashboardSummaryUseCase;
import com.aegisnotify.notification.application.port.in.GetProviderCircuitBreakersUseCase;
import java.time.Duration;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

  private static final Duration SUMMARY_WINDOW = Duration.ofHours(24);

  private final GetDashboardSummaryUseCase getDashboardSummaryUseCase;
  private final GetProviderCircuitBreakersUseCase getProviderCircuitBreakersUseCase;

  public DashboardController(
      GetDashboardSummaryUseCase getDashboardSummaryUseCase,
      GetProviderCircuitBreakersUseCase getProviderCircuitBreakersUseCase) {
    this.getDashboardSummaryUseCase = getDashboardSummaryUseCase;
    this.getProviderCircuitBreakersUseCase = getProviderCircuitBreakersUseCase;
  }

  @GetMapping("/summary")
  public ResponseEntity<DashboardSummaryResponse> summary() {
    var response = getDashboardSummaryUseCase.getSummary(SUMMARY_WINDOW);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/circuit-breakers")
  public ResponseEntity<List<ProviderCircuitBreakerStatus>> circuitBreakers() {
    return ResponseEntity.ok(getProviderCircuitBreakersUseCase.getCircuitBreakers());
  }
}
