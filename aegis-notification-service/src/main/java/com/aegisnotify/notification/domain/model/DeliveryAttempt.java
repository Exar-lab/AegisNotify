package com.aegisnotify.notification.domain.model;

import java.time.Instant;

/** One observed provider invocation, including retries and circuit rejections. */
public record DeliveryAttempt(
    String providerName,
    String role,
    boolean success,
    String code,
    String errorDetail,
    long latencyMillis,
    Instant timestamp
) {
}
