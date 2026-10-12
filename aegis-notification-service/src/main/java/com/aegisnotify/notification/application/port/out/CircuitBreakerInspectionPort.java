package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.application.dto.ProviderCircuitBreakerStatus;
import java.util.List;

public interface CircuitBreakerInspectionPort {

  List<ProviderCircuitBreakerStatus> getProviderCircuitBreakerStatuses();
}
