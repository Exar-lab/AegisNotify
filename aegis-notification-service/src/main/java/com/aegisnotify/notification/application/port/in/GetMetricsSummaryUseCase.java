package com.aegisnotify.notification.application.port.in;

import com.aegisnotify.notification.application.dto.MetricsSummaryResponse;

public interface GetMetricsSummaryUseCase {

  MetricsSummaryResponse getSummary();
}
