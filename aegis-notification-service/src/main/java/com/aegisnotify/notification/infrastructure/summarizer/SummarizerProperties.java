package com.aegisnotify.notification.infrastructure.summarizer;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.context.annotation.Configuration;

/**
 * External configuration for the aggregation summarizer's Anthropic Messages
 * API adapter (issue #86, Slice 2). Mirrors {@link
 * com.aegisnotify.notification.infrastructure.config.NotificationAggregationProperties}'s
 * compact-constructor validation style: nullable/duration fields fall back
 * to a safe default when absent, then every field is fail-fast validated.
 *
 * <p>Unlike {@code ProviderConfig}'s primary provider credentials, {@code
 * api-key} is intentionally NOT fail-fast validated here (L3 of the
 * design) — aggregation defaults to disabled, so a blank key must not block
 * application startup. {@code AggregationConfig} is responsible for
 * degrading to {@code UnavailableSummarizerAdapter} when the key is blank or
 * aggregation itself is disabled; only the adapter that is actually wired
 * requires a non-blank key, and that check happens at bean-construction time
 * in {@code AggregationConfig}, not here.</p>
 *
 * @param baseUrl          Anthropic API base URL
 * @param apiKey           Anthropic API key; blank means "not configured"
 * @param model            model identifier sent as {@code model} in the
 *                         request body
 * @param anthropicVersion value sent as the {@code anthropic-version}
 *                         header
 * @param maxTokens        {@code max_tokens} sent in the request body
 * @param timeout          bounded response timeout; MUST be less than {@code
 *                         notification.aggregation.window} (D8) — validated
 *                         at startup by {@code AggregationConfig}, since
 *                         comparing against the sibling {@code
 *                         NotificationAggregationProperties} tree cannot
 *                         happen inside this record's own constructor
 * @param maxOutputChars   hard cap applied to the summarized body before it
 *                         is persisted as {@code aggregate_body} (L4)
 */
@ConfigurationProperties(prefix = "notification.aggregation.summarizer")
public record SummarizerProperties(
    String baseUrl,
    String apiKey,
    String model,
    String anthropicVersion,
    int maxTokens,
    Duration timeout,
    int maxOutputChars) {

  private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
  private static final String DEFAULT_MODEL = "claude-sonnet-4-5";
  private static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";
  private static final int DEFAULT_MAX_TOKENS = 512;
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
  private static final int DEFAULT_MAX_OUTPUT_CHARS = 2000;

  @ConstructorBinding
  public SummarizerProperties {
    baseUrl = blankToDefault(baseUrl, DEFAULT_BASE_URL);
    model = blankToDefault(model, DEFAULT_MODEL);
    anthropicVersion = blankToDefault(anthropicVersion, DEFAULT_ANTHROPIC_VERSION);
    apiKey = apiKey == null ? "" : apiKey;
    timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;

    requirePositive(maxTokens, "notification.aggregation.summarizer.max-tokens");
    requirePositiveDuration(timeout, "notification.aggregation.summarizer.timeout");
    requirePositive(maxOutputChars, "notification.aggregation.summarizer.max-output-chars");
  }

  /** Default-config convenience constructor: no key configured, safe defaults throughout. */
  public SummarizerProperties() {
    this(DEFAULT_BASE_URL, "", DEFAULT_MODEL, DEFAULT_ANTHROPIC_VERSION, DEFAULT_MAX_TOKENS,
        DEFAULT_TIMEOUT, DEFAULT_MAX_OUTPUT_CHARS);
  }

  public boolean hasApiKey() {
    return apiKey != null && !apiKey.isBlank();
  }

  private static String blankToDefault(String value, String defaultValue) {
    return (value == null || value.isBlank()) ? defaultValue : value;
  }

  private static void requirePositive(int value, String propertyName) {
    if (value <= 0) {
      throw new IllegalStateException(propertyName + " must be greater than zero");
    }
  }

  private static void requirePositiveDuration(Duration value, String propertyName) {
    if (value.isZero() || value.isNegative()) {
      throw new IllegalStateException(propertyName + " must be a positive duration");
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(SummarizerProperties.class)
  static class Registration {
  }
}
