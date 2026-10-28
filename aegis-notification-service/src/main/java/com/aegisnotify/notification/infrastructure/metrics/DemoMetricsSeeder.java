package com.aegisnotify.notification.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Seeds the in-process Micrometer meters that back the admin metrics screen.
 *
 * <p>The metrics page reads live {@link MeterRegistry} meters, not PostgreSQL,
 * so database fixtures alone leave it empty. This runner fills those meters
 * with a deterministic, realistic demo snapshot at startup.</p>
 *
 * <p>Disabled by default: enable it with
 * {@code aegis.demo.metrics.enabled=true} (or
 * {@code AEGIS_DEMO_METRICS_ENABLED=true}) only for local demos. Never enable
 * it in a deployed environment, where the meters must reflect real traffic.</p>
 */
@Component
@ConditionalOnProperty(prefix = "aegis.demo.metrics", name = "enabled", havingValue = "true")
public class DemoMetricsSeeder implements ApplicationRunner {

  private static final String REQUESTS_TOTAL = "aegisnotify.requests.total";
  private static final String DELIVERY_LATENCY = "aegisnotify.delivery.latency.seconds";
  private static final String PROVIDER_ERRORS = "aegisnotify.provider.error.count";
  private static final String FALLBACK_TRANSMISSIONS = "aegisnotify.fallback.transmissions";
  private static final String KAFKA_SUCCESS = "notification.kafka.consumer.success";
  private static final String KAFKA_FAILURE = "notification.kafka.consumer.failure";
  private static final String KAFKA_RETRY = "notification.kafka.consumer.retry";
  private static final String KAFKA_DLT = "notification.kafka.consumer.dlt";

  private final MeterRegistry meterRegistry;

  public DemoMetricsSeeder(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void run(ApplicationArguments args) {
    seedRequests();
    seedDeliveryLatency();
    seedProviderErrors();
    seedFallbackTransmissions();
    seedKafkaConsumer();
  }

  private void seedRequests() {
    seedChannelRequests("EMAIL", 12, 16, 10);
    seedChannelRequests("SMS", 10, 15, 10);
    seedChannelRequests("WHATSAPP", 9, 14, 10);
    seedChannelRequests("PUSH", 8, 14, 10);
  }

  private void seedChannelRequests(String channel, int high, int medium, int low) {
    meterRegistry.counter(REQUESTS_TOTAL, "channel", channel, "priority", "HIGH")
        .increment(high);
    meterRegistry.counter(REQUESTS_TOTAL, "channel", channel, "priority", "MEDIUM")
        .increment(medium);
    meterRegistry.counter(REQUESTS_TOTAL, "channel", channel, "priority", "LOW")
        .increment(low);
  }

  private void seedDeliveryLatency() {
    seedLatency("SendGrid", 30, 180, 37);
    seedLatency("Twilio SMS", 18, 240, 29);
    seedLatency("Twilio WhatsApp", 16, 260, 31);
    seedLatency("Firebase Cloud Messaging", 13, 210, 41);
  }

  private void seedLatency(String provider, int samples, long baseMillis, long stepMillis) {
    Timer timer = Timer.builder(DELIVERY_LATENCY)
        .tag("provider", provider)
        .register(meterRegistry);
    for (int i = 0; i < samples; i++) {
      timer.record(Duration.ofMillis(baseMillis + (i * stepMillis) % 900));
    }
  }

  private void seedProviderErrors() {
    seedProviderError("SendGrid", "503", 4);
    seedProviderError("SendGrid", "401", 2);
    seedProviderError("Twilio SMS", "429", 3);
    seedProviderError("Twilio WhatsApp", "503", 2);
    seedProviderError("Firebase Cloud Messaging", "500", 2);
  }

  private void seedProviderError(String provider, String errorCode, int count) {
    meterRegistry.counter(PROVIDER_ERRORS, "provider", provider, "error_code", errorCode)
        .increment(count);
  }

  private void seedFallbackTransmissions() {
    meterRegistry.counter(FALLBACK_TRANSMISSIONS).increment(6);
  }

  private void seedKafkaConsumer() {
    seedKafkaTopic("high-priority-topic", 38, 4, 3, 1);
    seedKafkaTopic("medium-priority-topic", 26, 2, 1, 0);
    seedKafkaTopic("low-priority-topic", 18, 1, 1, 0);
  }

  private void seedKafkaTopic(String topic, int success, int failure, int retry, int dlq) {
    meterRegistry.counter(KAFKA_SUCCESS, "topic", topic).increment(success);
    meterRegistry.counter(KAFKA_FAILURE, "topic", topic).increment(failure);
    meterRegistry.counter(KAFKA_RETRY, "topic", topic).increment(retry);
    meterRegistry.counter(KAFKA_DLT, "topic", topic).increment(dlq);
  }
}
