package com.aegisnotify.notification.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.dto.ProviderResult.Outcome;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.domain.enums.Channel;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerMetricsAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryMetricsAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.Map;
import java.util.function.Predicate;
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
 * Verifies Resilience4j's retry metrics — configured in {@code application.yml}
 * under {@code resilience4j.retry.instances} — are exported through the
 * Prometheus scrape endpoint (Spec Req 7, Design DR-7), and that the
 * YAML-bound {@code retry-exceptions} allow-list is correctly resolved at
 * runtime (Design DR-6, closing the FQCN-drift gap Slice B's hand-built
 * registry tests could not cover).
 */
@SpringBootTest(
    classes = RetryPrometheusMetricsTest.RetryMetricsTestConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "management.prometheus.metrics.export.enabled=true")
@ImportAutoConfiguration({MetricsAutoConfiguration.class,
    CompositeMeterRegistryAutoConfiguration.class,
    PrometheusMetricsExportAutoConfiguration.class,
    CircuitBreakerAutoConfiguration.class,
    CircuitBreakerMetricsAutoConfiguration.class,
    RetryAutoConfiguration.class,
    RetryMetricsAutoConfiguration.class})
class RetryPrometheusMetricsTest {

  private static final String RETRY_INSTANCE_NAME = "email-provider";

  @Autowired
  private ResilientNotificationProviderAdapter adapter;

  @Autowired
  private PrometheusMeterRegistry prometheusMeterRegistry;

  @Autowired
  private RetryRegistry retryRegistry;

  @Test
  void exposesRetryMetricsThroughPrometheusScrapeAfterRetriedCall() {
    adapter.send(Channel.EMAIL, "to", "body", "subject");

    String scrape = prometheusMeterRegistry.scrape();

    assertThat(scrape).contains("resilience4j_retry_calls");
    assertThat(scrape).contains("name=\"email-provider\"");
    assertThat(scrape).contains("kind=\"successful_with_retry\"");
  }

  @Test
  void yamlBoundRetryPredicateAllowsOnlyTransientProviderDeliveryException() {
    Predicate<Throwable> exceptionPredicate =
        retryRegistry.retry(RETRY_INSTANCE_NAME).getRetryConfig().getExceptionPredicate();

    assertThat(exceptionPredicate.test(new TransientProviderDeliveryException("transient")))
        .isTrue();
    assertThat(exceptionPredicate.test(new PermanentProviderDeliveryException("permanent")))
        .isFalse();
    assertThat(exceptionPredicate.test(new RuntimeException("unrelated"))).isFalse();
  }

  @Test
  void noCustomRetryMetricIsIntroducedInProviderMetrics() {
    long retryRelatedMembers = java.util.Arrays.stream(ProviderMetrics.class.getDeclaredFields())
        .map(java.lang.reflect.Field::getName)
        .filter(name -> name.toLowerCase(java.util.Locale.ROOT).contains("retry"))
        .count();
    retryRelatedMembers += java.util.Arrays.stream(ProviderMetrics.class.getDeclaredMethods())
        .map(java.lang.reflect.Method::getName)
        .filter(name -> name.toLowerCase(java.util.Locale.ROOT).contains("retry"))
        .count();

    assertThat(retryRelatedMembers).isZero();
  }

  @Configuration(proxyBeanMethods = false)
  static class RetryMetricsTestConfiguration {

    @Bean
    NotificationProviderRouter notificationProviderRouter() {
      SendGridEmailProviderAdapter primary = mock(SendGridEmailProviderAdapter.class);
      when(primary.send(Channel.EMAIL, "to", "body", "subject"))
          .thenReturn(new ProviderResult(Outcome.FAILED, "SendGrid", "429 rate limited", true))
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
        RetryRegistry retryRegistry,
        MeterRegistry meterRegistry) {
      return new ResilientNotificationProviderAdapter(
          notificationProviderRouter, secondaryProvidersByChannel, circuitBreakerRegistry,
          retryRegistry, meterRegistry);
    }
  }
}
