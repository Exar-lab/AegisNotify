package com.aegisnotify.notification.infrastructure.config;

import com.aegisnotify.notification.application.port.out.NotificationMetricsPort;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.Priority;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Micrometer-backed {@link NotificationMetricsPort} implementation. */
@Component
public class MicrometerNotificationMetricsAdapter implements NotificationMetricsPort {

  private static final String METER_REQUESTS_TOTAL = "aegisnotify.requests.total";

  private final MeterRegistry meterRegistry;

  public MicrometerNotificationMetricsAdapter(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void recordRequest(Channel channel, Priority priority) {
    meterRegistry.counter(METER_REQUESTS_TOTAL,
        "channel", channel.name(),
        "priority", priority.name()
    ).increment();
  }
}
