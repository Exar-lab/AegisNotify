package com.aegisnotify.notification.infrastructure.metrics;

import com.aegisnotify.notification.application.dto.MetricsSummaryResponse;
import com.aegisnotify.notification.application.dto.MetricsSummaryResponse.ChannelRequestCount;
import com.aegisnotify.notification.application.dto.MetricsSummaryResponse.DeliveryLatency;
import com.aegisnotify.notification.application.dto.MetricsSummaryResponse.KafkaConsumerCounts;
import com.aegisnotify.notification.application.dto.MetricsSummaryResponse.ProviderErrorCount;
import com.aegisnotify.notification.application.port.out.MetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Builds the metrics summary from live in-process Micrometer meters. Meter names and tag keys
 * mirror the recording classes (MicrometerNotificationMetricsAdapter, ProviderMetrics,
 * NotificationKafkaMetrics, ResilientNotificationProviderAdapter).
 */
@Component
public class MetricsSnapshotAdapter implements MetricsPort {

  private static final String REQUESTS_TOTAL = "aegisnotify.requests.total";
  private static final String DELIVERY_LATENCY = "aegisnotify.delivery.latency.seconds";
  private static final String PROVIDER_ERRORS = "aegisnotify.provider.error.count";
  private static final String FALLBACK_TRANSMISSIONS = "aegisnotify.fallback.transmissions";
  private static final String KAFKA_SUCCESS = "notification.kafka.consumer.success";
  private static final String KAFKA_FAILURE = "notification.kafka.consumer.failure";
  private static final String KAFKA_RETRY = "notification.kafka.consumer.retry";
  private static final String KAFKA_DLT = "notification.kafka.consumer.dlt";

  private final MeterRegistry meterRegistry;

  public MetricsSnapshotAdapter(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public MetricsSummaryResponse snapshot() {
    return new MetricsSummaryResponse(
        requestsByChannel(),
        deliveryLatency(),
        providerErrors(),
        sumCounters(FALLBACK_TRANSMISSIONS),
        new KafkaConsumerCounts(
            sumCounters(KAFKA_SUCCESS),
            sumCounters(KAFKA_FAILURE),
            sumCounters(KAFKA_RETRY),
            sumCounters(KAFKA_DLT)));
  }

  private List<ChannelRequestCount> requestsByChannel() {
    Map<String, Long> byChannel = new TreeMap<>();
    for (Counter counter : counters(REQUESTS_TOTAL)) {
      String channel = counter.getId().getTag("channel");
      if (channel != null) {
        byChannel.merge(channel, (long) counter.count(), Long::sum);
      }
    }
    return byChannel.entrySet().stream()
        .map(e -> new ChannelRequestCount(e.getKey(), e.getValue()))
        .toList();
  }

  private DeliveryLatency deliveryLatency() {
    long count = 0;
    double totalSeconds = 0;
    double maxSeconds = 0;
    for (Timer timer : meterRegistry.find(DELIVERY_LATENCY).timers()) {
      count += timer.count();
      totalSeconds += timer.totalTime(TimeUnit.SECONDS);
      maxSeconds = Math.max(maxSeconds, timer.max(TimeUnit.SECONDS));
    }
    double mean = count == 0 ? 0.0 : totalSeconds / count;
    return new DeliveryLatency(count, mean, maxSeconds);
  }

  private List<ProviderErrorCount> providerErrors() {
    return counters(PROVIDER_ERRORS).stream()
        .map(c -> new ProviderErrorCount(
            c.getId().getTag("provider"),
            c.getId().getTag("error_code"),
            (long) c.count()))
        .sorted(Comparator
            .comparing(ProviderErrorCount::provider,
                Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(ProviderErrorCount::errorCode,
                Comparator.nullsFirst(Comparator.naturalOrder())))
        .toList();
  }

  private long sumCounters(String name) {
    return counters(name).stream().mapToLong(c -> (long) c.count()).sum();
  }

  private Collection<Counter> counters(String name) {
    return meterRegistry.find(name).counters();
  }
}
