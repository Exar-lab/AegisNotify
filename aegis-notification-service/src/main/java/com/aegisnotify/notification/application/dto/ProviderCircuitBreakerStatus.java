package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.enums.Channel;

public record ProviderCircuitBreakerStatus(
    String key,
    String name,
    Channel channel,
    String role,
    String fallbackProvider,
    String state
) {}
