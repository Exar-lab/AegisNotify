package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.application.dto.ProviderCallMetrics;
import java.time.Instant;
import java.util.List;

public interface ProviderCallMetricsPort {
  List<ProviderCallMetrics> aggregate(Instant from, Instant today, Instant to);
}
