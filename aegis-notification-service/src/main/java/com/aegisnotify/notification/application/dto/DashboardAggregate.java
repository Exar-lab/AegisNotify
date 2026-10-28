package com.aegisnotify.notification.application.dto;

public record DashboardAggregate(
    long totalCount,
    long sentCount,
    long sentViaFallbackCount,
    long failedCount,
    long failedCriticalCount,
    Double avgTerminalLatencyMillis
) {}
