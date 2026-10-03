package com.aegisnotify.notification.infrastructure.persistence.repository;

import java.time.Instant;

/**
 * {@code createdAt}/{@code updatedAt} pair for a terminal (SENT or
 * SENT_VIA_FALLBACK) notification, produced by {@link
 * SpringDataNotificationRepository#findTerminalTimestampsSince}. Kept as a
 * lightweight projection — not the full entity — because the dashboard
 * endpoint only needs these two columns to compute average delivery
 * latency.
 */
public interface NotificationLatencyProjection {

  Instant getCreatedAt();

  Instant getUpdatedAt();
}
