package com.aegisnotify.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class NotificationKafkaMetricsTest {

  @Test
  void incrementsSuccessFailureRetryAndDlqCountersWithTopicTag() {
    String topic = "high-priority-topic";
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    NotificationKafkaMetrics metrics = new NotificationKafkaMetrics(meterRegistry);

    metrics.recordSuccess(topic);
    metrics.recordFailure(topic);
    metrics.recordRetry(topic);
    metrics.recordDlq(topic);

    assertThat(meterRegistry.find("notification.kafka.consumer.success")
        .tags("topic", topic)
        .counter().count()).isEqualTo(1.0d);
    assertThat(meterRegistry.find("notification.kafka.consumer.failure")
        .tags("topic", topic)
        .counter().count()).isEqualTo(1.0d);
    assertThat(meterRegistry.find("notification.kafka.consumer.retry")
        .tags("topic", topic)
        .counter().count()).isEqualTo(1.0d);
    assertThat(meterRegistry.find("notification.kafka.consumer.dlt")
        .tags("topic", topic)
        .counter().count()).isEqualTo(1.0d);
  }
}
