package com.aegisnotify.notification.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import com.aegisnotify.notification.application.dto.MetricsSummaryResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MetricsSnapshotAdapterTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final MetricsSnapshotAdapter adapter = new MetricsSnapshotAdapter(registry);

  @Test
  void snapshot_emptyRegistry_returnsZeros() {
    MetricsSummaryResponse result = adapter.snapshot();

    assertThat(result.requestsByChannel()).isEmpty();
    assertThat(result.providerErrors()).isEmpty();
    assertThat(result.deliveryLatency().count()).isZero();
    assertThat(result.deliveryLatency().meanSeconds()).isZero();
    assertThat(result.deliveryLatency().maxSeconds()).isZero();
    assertThat(result.fallbackTransmissions()).isZero();
    assertThat(result.kafkaConsumer().success()).isZero();
  }

  @Test
  void snapshot_seededMeters_aggregatesAcrossTags() {
    registry.counter("aegisnotify.requests.total", "channel", "EMAIL", "priority", "HIGH")
        .increment(100);
    registry.counter("aegisnotify.requests.total", "channel", "EMAIL", "priority", "LOW")
        .increment(28);
    registry.counter("aegisnotify.requests.total", "channel", "SMS", "priority", "HIGH")
        .increment(5);

    registry.timer("aegisnotify.delivery.latency.seconds", "provider", "SendGrid")
        .record(Duration.ofMillis(500));
    registry.timer("aegisnotify.delivery.latency.seconds", "provider", "Twilio")
        .record(Duration.ofMillis(1500));

    registry.counter("aegisnotify.provider.error.count",
        "provider", "SendGrid", "error_code", "500").increment(3);
    registry.counter("aegisnotify.fallback.transmissions").increment(12);

    registry.counter("notification.kafka.consumer.success", "topic", "a").increment(900);
    registry.counter("notification.kafka.consumer.success", "topic", "b").increment(80);
    registry.counter("notification.kafka.consumer.failure", "topic", "a").increment(4);
    registry.counter("notification.kafka.consumer.retry", "topic", "a").increment(11);
    registry.counter("notification.kafka.consumer.dlt", "topic", "a").increment(1);

    MetricsSummaryResponse result = adapter.snapshot();

    assertThat(result.requestsByChannel()).containsExactly(
        new MetricsSummaryResponse.ChannelRequestCount("EMAIL", 128),
        new MetricsSummaryResponse.ChannelRequestCount("SMS", 5));
    assertThat(result.deliveryLatency().count()).isEqualTo(2);
    assertThat(result.deliveryLatency().meanSeconds()).isCloseTo(1.0, offset(0.001));
    assertThat(result.deliveryLatency().maxSeconds()).isCloseTo(1.5, offset(0.001));
    assertThat(result.providerErrors()).containsExactly(
        new MetricsSummaryResponse.ProviderErrorCount("SendGrid", "500", 3));
    assertThat(result.fallbackTransmissions()).isEqualTo(12);
    assertThat(result.kafkaConsumer())
        .isEqualTo(new MetricsSummaryResponse.KafkaConsumerCounts(980, 4, 11, 1));
  }
}
