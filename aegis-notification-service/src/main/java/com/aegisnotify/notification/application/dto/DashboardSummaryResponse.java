package com.aegisnotify.notification.application.dto;

public record DashboardSummaryResponse(
    MetricDelta totalRequests,
    MetricDelta successRate,
    MetricDelta fallbackActivations,
    MetricDelta avgDeliveryLatency
) {}
