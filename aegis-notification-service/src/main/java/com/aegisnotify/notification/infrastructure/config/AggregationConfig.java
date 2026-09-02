package com.aegisnotify.notification.infrastructure.config;

import com.aegisnotify.notification.domain.model.AggregationSettings;
import java.time.Clock;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Maps {@link NotificationAggregationProperties} (Spring-bound) to the pure
 * domain {@link AggregationSettings} value object (X3 of the design).
 * ArchUnit bans {@code org.springframework..} from {@code ..domain..} and
 * {@code ..application.port..}, so this bean-to-domain-object mapping must
 * live in infrastructure — application services depend on the domain object,
 * never on the Spring-bound properties record directly.
 *
 * <p>Slice 1 does not yet bind {@code excluded-templates}/{@code
 * excluded-channels} (D13 config wiring is Slice 3 scope) — both default to
 * empty sets here, meaning {@link com.aegisnotify.notification.domain.model
 * .AggregationPolicy}'s exclusion check is exercised and correct today, just
 * with nothing yet configured to exclude.</p>
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
        Set.of(),
        Set.of(),
        properties.claimLease(),
        properties.maxAttempts()
    );
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
