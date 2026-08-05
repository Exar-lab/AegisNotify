package com.aegisnotify.notification.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.dto.ProviderResult.Outcome;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.domain.enums.Channel;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerMetricsAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies Resilience4j's circuit breaker metrics — configured in
 * {@code application.yml} under {@code resilience4j.circuitbreaker.instances}
 * — are exported through the Prometheus scrape endpoint, satisfying issue
 * #33's metrics acceptance criterion.
 */
@SpringBootTest(
    classes = CircuitBreakerPrometheusMetricsTest.PrometheusMetricsTestConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "management.prometheus.metrics.export.enabled=true")
@ImportAutoConfiguration({MetricsAutoConfiguration.class,
    CompositeMeterRegistryAutoConfiguration.class,
    PrometheusMetricsExportAutoConfiguration.class,
    CircuitBreakerAutoConfiguration.class,
    CircuitBreakerMetricsAutoConfiguration.class})
class CircuitBreakerPrometheusMetricsTest {

  @Autowired
  private ResilientNotificationProviderAdapter adapter;

  @Autowired
  private PrometheusMeterRegistry prometheusMeterRegistry;

  @Test
  void exposesCircuitBreakerMetricsThroughPrometheusScrape() {
    adapter.send(Channel.EMAIL, "to", "body", "subject");

    String scrape = prometheusMeterRegistry.scrape();

    assertThat(scrape).contains("resilience4j_circuitbreaker_calls_seconds_count");
    assertThat(scrape).contains("name=\"email-provider\"");
    assertThat(scrape).contains("resilience4j_circuitbreaker_state");
  }

  @Configuration(proxyBeanMethods = false)
  static class PrometheusMetricsTestConfiguration {

    @Bean
    NotificationProviderRouter notificationProviderRouter() {
      SendGridEmailProviderAdapter primary = mock(SendGridEmailProviderAdapter.class);
      when(primary.send(Channel.EMAIL, "to", "body", "subject"))
          .thenReturn(new ProviderResult(Outcome.SENT, "SendGrid", null));
      return new NotificationProviderRouter(
          primary, mock(TwilioSmsProviderAdapter.class),
          mock(TwilioWhatsAppProviderAdapter.class), mock(FirebasePushProviderAdapter.class));
    }

    @Bean
    Map<Channel, NotificationProviderPort> secondaryProvidersByChannel() {
      return Map.of(Channel.EMAIL, mock(NotificationProviderPort.class));
    }

    @Bean
    ResilientNotificationProviderAdapter resilientNotificationProviderAdapter(
        NotificationProviderRouter notificationProviderRouter,
        Map<Channel, NotificationProviderPort> secondaryProvidersByChannel,
        CircuitBreakerRegistry circuitBreakerRegistry,
        MeterRegistry meterRegistry) {
      return new ResilientNotificationProviderAdapter(
          notificationProviderRouter, secondaryProvidersByChannel, circuitBreakerRegistry,
          meterRegistry);
    }
  }
}
