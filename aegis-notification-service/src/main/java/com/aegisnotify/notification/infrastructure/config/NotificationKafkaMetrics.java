package com.aegisnotify.notification.infrastructure.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class NotificationKafkaMetrics {

  private static final String METER_SUCCESS = "notification.kafka.consumer.success";
  private static final String METER_FAILURE = "notification.kafka.consumer.failure";
  private static final String METER_RETRY = "notification.kafka.consumer.retry";
  private static final String METER_DLT = "notification.kafka.consumer.dlt";

  private final MeterRegistry meterRegistry;

  public NotificationKafkaMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordSuccess(String topic) {
    increment(METER_SUCCESS, topic);
  }

  public void recordFailure(String topic) {
    increment(METER_FAILURE, topic);
  }

  public void recordRetry(String topic) {
    increment(METER_RETRY, topic);
  }

  public void recordDlq(String topic) {
    increment(METER_DLT, topic);
  }

  private void increment(String meterName, String topic) {
    meterRegistry.counter(meterName, "topic", topic).increment();
  }
}
