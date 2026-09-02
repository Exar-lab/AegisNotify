package com.aegisnotify.notification.infrastructure.config;

import com.aegisnotify.notification.application.port.out.AggregationSummarizerPort;
import com.aegisnotify.notification.domain.model.AggregationSettings;
import com.aegisnotify.notification.infrastructure.summarizer.AnthropicMessagesSummarizerAdapter;
import com.aegisnotify.notification.infrastructure.summarizer.SummarizerProperties;
import com.aegisnotify.notification.infrastructure.summarizer.UnavailableSummarizerAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Maps {@link NotificationAggregationProperties} (Spring-bound) to the pure
 * domain {@link AggregationSettings} value object (X3 of the design).
 * ArchUnit bans {@code org.springframework..} from {@code ..domain..} and
 * {@code ..application.port..}, so this bean-to-domain-object mapping must
 * live in infrastructure — application services depend on the domain object,
 * never on the Spring-bound properties record directly.
 *
 * <p>{@code excluded-templates}/{@code excluded-channels} (D13, Slice 3)
 * bind straight from {@link NotificationAggregationProperties} into {@link
 * AggregationSettings}, completing X1: {@link com.aegisnotify.notification
 * .domain.model.AggregationPolicy}'s exclusion check has accepted these
 * fields since Slice 1, but nothing was configured to exclude until this
 * mapping existed.</p>
 *
 * <p><strong>No {@code @EnableScheduling} here</strong> (K7, revised in
 * rev 2 of the design): the single unconditional scheduling bootstrap lives
 * in {@link SchedulingConfig}, so the mandatory outbox relay never depends
 * on aggregation being enabled.</p>
 */
@Configuration(proxyBeanMethods = false)
public class AggregationConfig {

  @Bean
  public AggregationSettings aggregationSettings(NotificationAggregationProperties properties) {
    return new AggregationSettings(
        properties.enabled(),
        properties.window(),
        properties.requireSameTemplate(),
        properties.maxGroupSize(),
        properties.excludedTemplates(),
        properties.excludedChannels(),
        properties.claimLease(),
        properties.maxAttempts()
    );
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * Wires the {@link AggregationSummarizerPort} bean (L1-L3 of the design,
   * Slice 2). Deliberately NOT expressed via {@code @ConditionalOnMissingBean}
   * (the design's literal wording) — this repo's own {@code ProviderConfig}
   * establishes the precedent of a single {@code @Bean} factory method that
   * inspects configuration and returns whichever concrete adapter applies,
   * with no Spring conditional annotations. Same observable outcome (a
   * missing optional dependency degrades to a safe default instead of
   * blocking startup, mirroring {@code ProviderConfig.NO_SECONDARY_PROVIDER}),
   * simpler and directly unit-testable with plain objects (see {@code
   * AggregationConfigTest}).
   *
   * <p>When aggregation is enabled AND an API key is configured, this also
   * enforces D8 at startup: {@code summarizer.timeout} must be strictly less
   * than {@code aggregation.window}, since a summarizer call that could
   * itself run as long as (or longer than) the window it is trying to beat
   * would defeat the whole cost/latency budget the breaker exists to
   * protect. It additionally enforces {@code summarizer.timeout} strictly
   * less than {@code aggregation.claim-lease} (review-resilience, CRITICAL):
   * the summarizer HTTP call runs synchronously against a buffered row held
   * {@code CLAIMED} with no DB transaction open (B3), so a timeout at or
   * beyond the claim lease lets a concurrent scheduler tick see the row as
   * lease-expired and re-claim + re-process it while the first call is
   * still in flight — producing two independent outbox writes, i.e.
   * duplicate delivery, for one notification.</p>
   */
  @Bean
  public AggregationSummarizerPort aggregationSummarizerPort(
      NotificationAggregationProperties aggregationProperties,
      SummarizerProperties summarizerProperties,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    if (!aggregationProperties.enabled() || !summarizerProperties.hasApiKey()) {
      return new UnavailableSummarizerAdapter();
    }
    requireTimeoutLessThanWindow(summarizerProperties.timeout(), aggregationProperties.window());
    requireTimeoutLessThanClaimLease(
        summarizerProperties.timeout(), aggregationProperties.claimLease());
    return new AnthropicMessagesSummarizerAdapter(
        WebClient.builder(), summarizerProperties, circuitBreakerRegistry);
  }

  private void requireTimeoutLessThanWindow(Duration timeout, Duration window) {
    if (timeout.compareTo(window) >= 0) {
      throw new IllegalStateException(
          "notification.aggregation.summarizer.timeout must be less than "
              + "notification.aggregation.window");
    }
  }

  private void requireTimeoutLessThanClaimLease(Duration timeout, Duration claimLease) {
    if (timeout.compareTo(claimLease) >= 0) {
      throw new IllegalStateException(
          "notification.aggregation.summarizer.timeout must be less than "
              + "notification.aggregation.claim-lease");
    }
  }
}
