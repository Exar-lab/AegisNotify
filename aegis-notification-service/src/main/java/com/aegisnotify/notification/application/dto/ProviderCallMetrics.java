package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.enums.Channel;

/** Persisted delivery attempts in the requested window, excluding rejected/unconfigured calls. */
public record ProviderCallMetrics(
    Channel channel, String role, long calls, long callsToday, long successes) {}
