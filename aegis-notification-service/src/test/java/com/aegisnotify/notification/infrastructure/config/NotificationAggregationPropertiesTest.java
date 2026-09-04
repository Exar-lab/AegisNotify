package com.aegisnotify.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisnotify.notification.domain.enums.Channel;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Unit tests for {@link NotificationAggregationProperties}'s compact
 * constructor: default values, and fail-fast validation mirroring {@link
 * NotificationKafkaProperties}'s style. The {@code excluded-templates}/
 * {@code excluded-channels} (D13, Slice 3) tests bind from the REAL {@code
 * application.yml} via {@link ApplicationContextRunner}, mirroring {@code
 * NotificationKafkaPropertiesTest}'s pattern, to prove the config-binding
 * path end to end, not just the compact constructor in isolation.
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class NotificationAggregationPropertiesTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withInitializer(new ConfigDataApplicationContextInitializer())
      .withUserConfiguration(PropertiesConfiguration.class);

  @Test
  void defaultConstructor_disabledWithSafeDefaults() {
    NotificationAggregationProperties properties = new NotificationAggregationProperties();

    assertFalse(properties.enabled());
    assertEquals(Duration.ofMinutes(5), properties.window());
    assertEquals(Duration.ofSeconds(10), properties.pollInterval());
    assertEquals(Duration.ofMinutes(2), properties.claimLease());
    assertEquals(20, properties.maxGroupSize());
    assertEquals(3, properties.maxAttempts());
    assertEquals(true, properties.requireSameTemplate());
    assertTrue(properties.excludedTemplates().isEmpty());
    assertTrue(properties.excludedChannels().isEmpty());
  }

  /** Task 3.7 (D13): the real {@code application.yml} default is an unset (blank) env var. */
  @Test
  void loadsRealApplicationYaml_excludedTemplatesAndChannelsEmptyByDefault() {
    this.contextRunner.run(context -> {
      NotificationAggregationProperties properties =
          context.getBean(NotificationAggregationProperties.class);

      assertThat(properties.excludedTemplates()).isEmpty();
      assertThat(properties.excludedChannels()).isEmpty();
    });
  }

  /** Task 3.7 (D13): env-var override binds a comma-separated list for both fields. */
  @Test
  void realApplicationYamlBindsExcludedTemplatesAndChannelsFromEnvOverride() {
    this.contextRunner
        .withSystemProperties(
            "NOTIFICATION_AGGREGATION_EXCLUDED_TEMPLATES=regulated-notice,billing-statement",
            "NOTIFICATION_AGGREGATION_EXCLUDED_CHANNELS=SMS,PUSH")
        .run(context -> {
          NotificationAggregationProperties properties =
              context.getBean(NotificationAggregationProperties.class);

          assertThat(properties.excludedTemplates())
              .containsExactlyInAnyOrder("regulated-notice", "billing-statement");
          assertThat(properties.excludedChannels())
              .containsExactlyInAnyOrder(Channel.SMS, Channel.PUSH);
        });
  }

  /** Task 3.7 (D13): direct property override (not env var) binds identically. */
  @Test
  void bindsExcludedTemplatesAndChannelsFromDirectPropertyOverride() {
    this.contextRunner
        .withPropertyValues(
            "notification.aggregation.excluded-templates=regulated-notice",
            "notification.aggregation.excluded-channels=WHATSAPP")
        .run(context -> {
          NotificationAggregationProperties properties =
              context.getBean(NotificationAggregationProperties.class);

          assertThat(properties.excludedTemplates()).containsExactly("regulated-notice");
          assertThat(properties.excludedChannels()).containsExactly(Channel.WHATSAPP);
        });
  }

  @Test
  void compactConstructor_nullWindow_defaultsToFiveMinutes() {
    NotificationAggregationProperties properties = new NotificationAggregationProperties(
        true, null, Duration.ofSeconds(10), true, 20, Duration.ofMinutes(2), 3);

    assertEquals(Duration.ofMinutes(5), properties.window());
  }

  @Test
  void compactConstructor_zeroWindow_throws() {
    assertThrows(IllegalStateException.class, () -> new NotificationAggregationProperties(
        true, Duration.ZERO, Duration.ofSeconds(10), true, 20, Duration.ofMinutes(2), 3));
  }

  @Test
  void compactConstructor_negativePollInterval_throws() {
    assertThrows(IllegalStateException.class, () -> new NotificationAggregationProperties(
        true, Duration.ofMinutes(5), Duration.ofSeconds(-1), true, 20,
        Duration.ofMinutes(2), 3));
  }

  @Test
  void compactConstructor_zeroMaxGroupSize_throws() {
    assertThrows(IllegalStateException.class, () -> new NotificationAggregationProperties(
        true, Duration.ofMinutes(5), Duration.ofSeconds(10), true, 0,
        Duration.ofMinutes(2), 3));
  }

  @Test
  void compactConstructor_zeroMaxAttempts_throws() {
    assertThrows(IllegalStateException.class, () -> new NotificationAggregationProperties(
        true, Duration.ofMinutes(5), Duration.ofSeconds(10), true, 20,
        Duration.ofMinutes(2), 0));
  }

  @Test
  void compactConstructor_zeroClaimLease_throws() {
    assertThrows(IllegalStateException.class, () -> new NotificationAggregationProperties(
        true, Duration.ofMinutes(5), Duration.ofSeconds(10), true, 20,
        Duration.ZERO, 3));
  }

  @Configuration(proxyBeanMethods = false)
  @ComponentScan(
      basePackageClasses = NotificationAggregationProperties.class,
      useDefaultFilters = false,
      includeFilters = @ComponentScan.Filter(
          type = FilterType.ASSIGNABLE_TYPE,
          classes = NotificationAggregationProperties.Registration.class))
  static class PropertiesConfiguration {
  }
}
