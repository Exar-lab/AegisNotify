package com.aegisnotify.notification.application.dto;

/**
 * Dashboard KPI tile values for the admin frontend (AegisNotify
 * FRONTEND_REQUIREMENTS.md §5.1). {@code successRatePercent} is {@code 0}
 * when {@code totalRequests} is {@code 0} (no activity in the window, not a
 * failure).
 */
public record DashboardSummaryResponse(
    long totalRequests,
    double successRatePercent,
    long fallbackActivations,
    long avgDeliveryLatencyMillis
) {
}
