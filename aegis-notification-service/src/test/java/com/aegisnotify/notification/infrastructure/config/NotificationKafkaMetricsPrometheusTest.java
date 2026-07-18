package com.aegisnotify.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    classes = NotificationKafkaMetricsPrometheusTest.PrometheusMetricsTestConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "management.prometheus.metrics.export.enabled=true")
@ImportAutoConfiguration({MetricsAutoConfiguration.class,
    CompositeMeterRegistryAutoConfiguration.class,
    PrometheusMetricsExportAutoConfiguration.class})
@ActiveProfiles("test")
class NotificationKafkaMetricsPrometheusTest {

  @Autowired
  private NotificationKafkaMetrics notificationKafkaMetrics;

  @Autowired
  private PrometheusMeterRegistry prometheusMeterRegistry;

  @Test
  void exposesKafkaCountersThroughPrometheusScrapeWithTopicTag() {
    String topic = "high-priority-topic";

    notificationKafkaMetrics.recordSuccess(topic);
    notificationKafkaMetrics.recordFailure(topic);
    notificationKafkaMetrics.recordRetry(topic);
    notificationKafkaMetrics.recordDlq(topic);

    String scrape = prometheusMeterRegistry.scrape();
    assertThat(prometheusMeterRegistry).isNotNull();
    assertThat(scrape).contains(
        "notification_kafka_consumer_success_total{topic=\"" + topic + "\"} 1.0");
    assertThat(scrape).contains(
        "notification_kafka_consumer_failure_total{topic=\"" + topic + "\"} 1.0");
    assertThat(scrape).contains(
        "notification_kafka_consumer_retry_total{topic=\"" + topic + "\"} 1.0");
    assertThat(scrape).contains(
        "notification_kafka_consumer_dlt_total{topic=\"" + topic + "\"} 1.0");
  }

  @Configuration(proxyBeanMethods = false)
  @Import(NotificationKafkaMetrics.class)
  static class PrometheusMetricsTestConfiguration {
  }
}
