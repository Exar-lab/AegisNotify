package com.aegisnotify.notification.application.dto;

/**
 * Raw counts over a time window, as returned by {@link
 * com.aegisnotify.notification.application.port.out.NotificationRepository#aggregateSince}.
 * {@code avgTerminalLatencyMillis} is {@code null} when no notification in
 * the window reached a terminal {@code SENT}/{@code SENT_VIA_FALLBACK}
 * state yet.
 */
public record DashboardAggregate(
    long totalCount,
    long sentCount,
    long sentViaFallbackCount,
    long failedCriticalCount,
    Double avgTerminalLatencyMillis
) {
}
