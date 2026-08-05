package com.aegisnotify.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.Priority;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MicrometerNotificationMetricsAdapterTest {

  @Test
  void recordRequest_incrementsCounterTaggedByChannelAndPriority() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MicrometerNotificationMetricsAdapter adapter =
        new MicrometerNotificationMetricsAdapter(meterRegistry);

    adapter.recordRequest(Channel.EMAIL, Priority.HIGH);
    adapter.recordRequest(Channel.EMAIL, Priority.HIGH);
    adapter.recordRequest(Channel.SMS, Priority.LOW);

    assertThat(meterRegistry.find("aegisnotify.requests.total")
        .tags("channel", "EMAIL", "priority", "HIGH")
        .counter().count()).isEqualTo(2.0d);
    assertThat(meterRegistry.find("aegisnotify.requests.total")
        .tags("channel", "SMS", "priority", "LOW")
        .counter().count()).isEqualTo(1.0d);
  }
}
