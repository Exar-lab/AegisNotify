package com.aegisnotify.notification.application.port.in;

import com.aegisnotify.notification.application.dto.ProviderCircuitBreakerStatus;
import java.util.List;

public interface GetProviderCircuitBreakersUseCase {

  List<ProviderCircuitBreakerStatus> getCircuitBreakers();
}
