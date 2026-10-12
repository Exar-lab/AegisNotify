package com.aegisnotify.notification.application.dto;

public record MetricDelta(
    double currentValue,
    double previousValue,
    Double deltaPercent,
    Double deltaPoints
) {}
