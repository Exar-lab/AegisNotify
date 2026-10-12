package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.ProviderCircuitBreakerStatus;
import com.aegisnotify.notification.application.port.in.GetProviderCircuitBreakersUseCase;
import com.aegisnotify.notification.application.port.out.CircuitBreakerInspectionPort;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GetProviderCircuitBreakersService implements GetProviderCircuitBreakersUseCase {

  private final CircuitBreakerInspectionPort circuitBreakerInspectionPort;

  public GetProviderCircuitBreakersService(
      CircuitBreakerInspectionPort circuitBreakerInspectionPort) {
    this.circuitBreakerInspectionPort = circuitBreakerInspectionPort;
  }

  @Override
  public List<ProviderCircuitBreakerStatus> getCircuitBreakers() {
    return circuitBreakerInspectionPort.getProviderCircuitBreakerStatuses();
  }
}
