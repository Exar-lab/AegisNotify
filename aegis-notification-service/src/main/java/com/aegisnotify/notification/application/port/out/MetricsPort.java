package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.application.dto.MetricsSummaryResponse;

/** Reads a live snapshot of the service's operational metrics. */
public interface MetricsPort {

  MetricsSummaryResponse snapshot();
}
