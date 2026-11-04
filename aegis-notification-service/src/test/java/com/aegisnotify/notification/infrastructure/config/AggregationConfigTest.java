package com.aegisnotify.notification.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisnotify.notification.application.port.out.AggregationSummarizerPort;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.model.AggregationSettings;
import com.aegisnotify.notification.infrastructure.summarizer.AnthropicMessagesSummarizerAdapter;
import com.aegisnotify.notification.infrastructure.summarizer.GroqChatCompletionsSummarizerAdapter;
import com.aegisnotify.notification.infrastructure.summarizer.GroqSummarizerProperties;
import com.aegisnotify.notification.infrastructure.summarizer.SummarizerProperties;
import com.aegisnotify.notification.infrastructure.summarizer.UnavailableSummarizerAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link AggregationConfig}'s Spring-properties-to-domain-object
 * mapping (X3 of the design) with no Spring context — plain object
 * construction is sufficient to prove the mapping is correct.
 */
class AggregationConfigTest {

  private static final String ANTHROPIC = "anthropic";
  private static final String GROQ = "groq";

  @Test
  void aggregationSettings_mapsAllPropertiesOntoDomainValueObject() {
    NotificationAggregationProperties properties = new NotificationAggregationProperties(
        true, Duration.ofMinutes(7), Duration.ofSeconds(15), false, 42,
        Duration.ofMinutes(3), 5);

    AggregationSettings settings = new AggregationConfig().aggregationSettings(properties);

    assertTrue(settings.enabled());
    assertEquals(Duration.ofMinutes(7), settings.window());
    assertEquals(false, settings.requireSameTemplate());
    assertEquals(42, settings.maxGroupSize());
    assertEquals(Duration.ofMinutes(3), settings.lease());
    assertEquals(5, settings.maxAttempts());
    assertTrue(settings.excludedTemplates().isEmpty());
    assertTrue(settings.excludedChannels().isEmpty());
  }

  /**
   * Task 3.8 (D13/X1 completion): {@code excluded-templates}/{@code
   * excluded-channels} must flow, unmodified, from the Spring-bound
   * properties record straight into the domain {@link AggregationSettings}
   * value object that {@link com.aegisnotify.notification.domain.model
   * .AggregationPolicy#isAggregatable} reads.
   */
  @Test
  void aggregationSettings_mapsExcludedTemplatesAndChannelsOntoDomainValueObject() {
    NotificationAggregationProperties properties = new NotificationAggregationProperties(
        true, Duration.ofMinutes(7), Duration.ofSeconds(15), false, 42,
        Duration.ofMinutes(3), 5, Set.of("regulated-notice", "billing-statement"),
        Set.of(Channel.SMS, Channel.PUSH));

    AggregationSettings settings = new AggregationConfig().aggregationSettings(properties);

    assertEquals(Set.of("regulated-notice", "billing-statement"), settings.excludedTemplates());
    assertEquals(Set.of(Channel.SMS, Channel.PUSH), settings.excludedChannels());
  }

  @Test
  void aggregationSettings_modelPriorityIsOffByDefaultAndMappedWhenEnabled() {
    NotificationAggregationProperties defaults = new NotificationAggregationProperties(
        true, Duration.ofMinutes(7), Duration.ofSeconds(15), false, 42,
        Duration.ofMinutes(3), 5);
    NotificationAggregationProperties enabled = new NotificationAggregationProperties(
        true, Duration.ofMinutes(7), Duration.ofSeconds(15), false, 42,
        Duration.ofMinutes(3), 5, Set.of(), Set.of(), true);

    assertFalse(new AggregationConfig().aggregationSettings(defaults).modelPriorityEnabled());
    assertTrue(new AggregationConfig().aggregationSettings(enabled).modelPriorityEnabled());
  }

  @Test
  void clock_producesUtcInstantCloseToNow() {
    Instant before = Instant.now();
    Instant clockInstant = new AggregationConfig().clock().instant();
    Instant after = Instant.now();

    assertNotNull(clockInstant);
    assertTrue(!clockInstant.isBefore(before) && !clockInstant.isAfter(after.plusSeconds(1)));
  }

  /**
   * L3 of the design (functionally, via an explicit {@code @Bean} decision
   * rather than {@code @ConditionalOnMissingBean} — see apply-progress
   * deviation note): aggregation disabled must degrade to the safe default,
   * never fail startup.
   */
  @Test
  void aggregationSummarizerPort_aggregationDisabled_returnsUnavailableAdapter() {
    NotificationAggregationProperties aggregationProperties = new NotificationAggregationProperties(
        false, Duration.ofMinutes(5), Duration.ofSeconds(10), true, 20,
        Duration.ofMinutes(2), 3);
    SummarizerProperties summarizerProperties = new SummarizerProperties(
        "https://api.anthropic.com", "a-real-key", "claude-sonnet-4-5", "2023-06-01", 512,
        Duration.ofSeconds(2), 2000);

    AggregationSummarizerPort port = new AggregationConfig().aggregationSummarizerPort(
        aggregationProperties, summarizerProperties, ANTHROPIC, new GroqSummarizerProperties(),
        CircuitBreakerRegistry.ofDefaults());

    assertInstanceOf(UnavailableSummarizerAdapter.class, port);
  }

  @Test
  void aggregationSummarizerPort_noApiKey_returnsUnavailableAdapter() {
    NotificationAggregationProperties aggregationProperties = new NotificationAggregationProperties(
        true, Duration.ofMinutes(5), Duration.ofSeconds(10), true, 20,
        Duration.ofMinutes(2), 3);
    SummarizerProperties summarizerProperties = new SummarizerProperties(
        "https://api.anthropic.com", "", "claude-sonnet-4-5", "2023-06-01", 512,
        Duration.ofSeconds(2), 2000);

    AggregationSummarizerPort port = new AggregationConfig().aggregationSummarizerPort(
        aggregationProperties, summarizerProperties, ANTHROPIC, new GroqSummarizerProperties(),
        CircuitBreakerRegistry.ofDefaults());

    assertInstanceOf(UnavailableSummarizerAdapter.class, port);
  }

  @Test
  void aggregationSummarizerPort_enabledWithApiKey_returnsAnthropicAdapter() {
    NotificationAggregationProperties aggregationProperties = new NotificationAggregationProperties(
        true, Duration.ofMinutes(5), Duration.ofSeconds(10), true, 20,
        Duration.ofMinutes(2), 3);
    SummarizerProperties summarizerProperties = new SummarizerProperties(
        "https://api.anthropic.com", "a-real-key", "claude-sonnet-4-5", "2023-06-01", 512,
        Duration.ofSeconds(2), 2000);

    AggregationSummarizerPort port = new AggregationConfig().aggregationSummarizerPort(
        aggregationProperties, summarizerProperties, ANTHROPIC, new GroqSummarizerProperties(),
        CircuitBreakerRegistry.ofDefaults());

    assertInstanceOf(AnthropicMessagesSummarizerAdapter.class, port);
  }

  /** D8: the summarizer timeout must be strictly less than the aggregation window. */
  @Test
  void aggregationSummarizerPort_timeoutNotLessThanWindow_throwsAtStartup() {
    NotificationAggregationProperties aggregationProperties = new NotificationAggregationProperties(
        true, Duration.ofSeconds(10), Duration.ofSeconds(10), true, 20,
        Duration.ofMinutes(2), 3);
    SummarizerProperties summarizerProperties = new SummarizerProperties(
        "https://api.anthropic.com", "a-real-key", "claude-sonnet-4-5", "2023-06-01", 512,
        Duration.ofSeconds(10), 2000);
    CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();

    assertThrows(IllegalStateException.class,
        () -> new AggregationConfig().aggregationSummarizerPort(
            aggregationProperties, summarizerProperties, ANTHROPIC,
            new GroqSummarizerProperties(), registry));
  }

  /**
   * Fix (review-resilience, CRITICAL): a config where {@code
   * summarizer.timeout >= claim-lease} but still {@code < window} used to
   * pass the timeout-vs-window check above yet still risked a concurrent
   * scheduler tick reclaiming a {@code CLAIMED} row mid-flight (duplicate
   * delivery), because nothing validated timeout against the claim lease.
   */
  @Test
  void aggregationSummarizerPort_timeoutNotLessThanClaimLease_throwsAtStartup() {
    NotificationAggregationProperties aggregationProperties = new NotificationAggregationProperties(
        true, Duration.ofMinutes(5), Duration.ofSeconds(10), true, 20,
        Duration.ofMinutes(2), 3);
    SummarizerProperties summarizerProperties = new SummarizerProperties(
        "https://api.anthropic.com", "a-real-key", "claude-sonnet-4-5", "2023-06-01", 512,
        Duration.ofMinutes(3), 2000);
    CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();

    assertThrows(IllegalStateException.class,
        () -> new AggregationConfig().aggregationSummarizerPort(
            aggregationProperties, summarizerProperties, ANTHROPIC,
            new GroqSummarizerProperties(), registry));
  }

  private static NotificationAggregationProperties enabledAggregation() {
    return new NotificationAggregationProperties(
        true, Duration.ofMinutes(5), Duration.ofSeconds(10), true, 20,
        Duration.ofMinutes(2), 3);
  }

  private static SummarizerProperties summarizerWithAnthropicKey(String apiKey, Duration timeout) {
    return new SummarizerProperties(
        "https://api.anthropic.com", apiKey, "claude-sonnet-4-5", "2023-06-01", 512,
        timeout, 2000);
  }

  private static GroqSummarizerProperties groqWithKey(String apiKey) {
    return new GroqSummarizerProperties(
        "https://api.groq.com/openai", apiKey, "llama-3.1-8b-instant");
  }

  @Test
  void aggregationSummarizerPort_groqVendorWithGroqKey_returnsGroqAdapter() {
    AggregationSummarizerPort port = new AggregationConfig().aggregationSummarizerPort(
        enabledAggregation(), summarizerWithAnthropicKey("", Duration.ofSeconds(2)), GROQ,
        groqWithKey("a-groq-key"), CircuitBreakerRegistry.ofDefaults());

    assertInstanceOf(GroqChatCompletionsSummarizerAdapter.class, port);
  }

  @Test
  void aggregationSummarizerPort_groqVendorIsCaseInsensitive() {
    AggregationSummarizerPort port = new AggregationConfig().aggregationSummarizerPort(
        enabledAggregation(), summarizerWithAnthropicKey("", Duration.ofSeconds(2)), "GROQ",
        groqWithKey("a-groq-key"), CircuitBreakerRegistry.ofDefaults());

    assertInstanceOf(GroqChatCompletionsSummarizerAdapter.class, port);
  }

  /** Groq selected: only the Groq key counts — an Anthropic key alone must not wire anything. */
  @Test
  void aggregationSummarizerPort_groqVendorWithoutGroqKey_returnsUnavailableAdapter() {
    AggregationSummarizerPort port = new AggregationConfig().aggregationSummarizerPort(
        enabledAggregation(), summarizerWithAnthropicKey("a-real-key", Duration.ofSeconds(2)),
        GROQ, groqWithKey(""), CircuitBreakerRegistry.ofDefaults());

    assertInstanceOf(UnavailableSummarizerAdapter.class, port);
  }

  /** Anthropic selected (default): a Groq key alone must not wire anything. */
  @Test
  void aggregationSummarizerPort_anthropicVendorWithOnlyGroqKey_returnsUnavailableAdapter() {
    AggregationSummarizerPort port = new AggregationConfig().aggregationSummarizerPort(
        enabledAggregation(), summarizerWithAnthropicKey("", Duration.ofSeconds(2)), ANTHROPIC,
        groqWithKey("a-groq-key"), CircuitBreakerRegistry.ofDefaults());

    assertInstanceOf(UnavailableSummarizerAdapter.class, port);
  }

  @Test
  void aggregationSummarizerPort_groqVendorAggregationDisabled_returnsUnavailableAdapter() {
    NotificationAggregationProperties disabled = new NotificationAggregationProperties(
        false, Duration.ofMinutes(5), Duration.ofSeconds(10), true, 20,
        Duration.ofMinutes(2), 3);

    AggregationSummarizerPort port = new AggregationConfig().aggregationSummarizerPort(
        disabled, summarizerWithAnthropicKey("", Duration.ofSeconds(2)), GROQ,
        groqWithKey("a-groq-key"), CircuitBreakerRegistry.ofDefaults());

    assertInstanceOf(UnavailableSummarizerAdapter.class, port);
  }

  /** D8 applies regardless of vendor. */
  @Test
  void aggregationSummarizerPort_groqVendorTimeoutNotLessThanWindow_throwsAtStartup() {
    NotificationAggregationProperties aggregationProperties = new NotificationAggregationProperties(
        true, Duration.ofSeconds(10), Duration.ofSeconds(10), true, 20,
        Duration.ofMinutes(2), 3);
    SummarizerProperties summarizerProperties =
        summarizerWithAnthropicKey("", Duration.ofSeconds(10));
    GroqSummarizerProperties groqProperties = groqWithKey("a-groq-key");
    CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();

    assertThrows(IllegalStateException.class,
        () -> new AggregationConfig().aggregationSummarizerPort(
            aggregationProperties, summarizerProperties, GROQ, groqProperties, registry));
  }

  /** The claim-lease invariant (duplicate-delivery guard) applies regardless of vendor. */
  @Test
  void aggregationSummarizerPort_groqVendorTimeoutNotLessThanClaimLease_throwsAtStartup() {
    SummarizerProperties summarizerProperties =
        summarizerWithAnthropicKey("", Duration.ofMinutes(3));
    NotificationAggregationProperties aggregationProperties = enabledAggregation();
    GroqSummarizerProperties groqProperties = groqWithKey("a-groq-key");
    CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();

    assertThrows(IllegalStateException.class,
        () -> new AggregationConfig().aggregationSummarizerPort(
            aggregationProperties, summarizerProperties, GROQ, groqProperties, registry));
  }

  @Test
  void groqSummarizerProperties_blankValues_fallBackToDefaultsAndReportNoKey() {
    GroqSummarizerProperties properties = new GroqSummarizerProperties(" ", null, "");

    assertEquals("https://api.groq.com/openai", properties.baseUrl());
    assertEquals("llama-3.1-8b-instant", properties.model());
    assertEquals("", properties.apiKey());
    assertTrue(!properties.hasApiKey());
  }
}
