package com.aegisnotify.notification.infrastructure.summarizer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.context.annotation.Configuration;

/**
 * Groq-specific credentials for the aggregation summarizer, used only when
 * {@code notification.aggregation.summarizer.vendor=groq}. Nested under the
 * summarizer prefix the same way {@code ProviderConfig} nests Vonage's
 * credentials under {@code .whatsapp.vonage.*}, keeping {@link
 * SummarizerProperties} free of per-vendor fields. The vendor-agnostic
 * settings ({@code timeout}, {@code max-tokens}, {@code max-output-chars})
 * are reused from {@link SummarizerProperties}, not duplicated here.
 *
 * <p>Like {@link SummarizerProperties#apiKey()}, {@code api-key} is NOT
 * fail-fast validated: {@code AggregationConfig} degrades to {@code
 * UnavailableSummarizerAdapter} when Groq is selected but no key is set.</p>
 *
 * @param baseUrl Groq OpenAI-compatible API base URL
 * @param apiKey  Groq API key, sent as a Bearer token; blank means "not
 *                configured"
 * @param model   model identifier sent as {@code model} in the request body
 */
@ConfigurationProperties(prefix = "notification.aggregation.summarizer.groq")
public record GroqSummarizerProperties(String baseUrl, String apiKey, String model) {

  private static final String DEFAULT_BASE_URL = "https://api.groq.com/openai";
  private static final String DEFAULT_MODEL = "qwen/qwen3.8-27b";

  @ConstructorBinding
  public GroqSummarizerProperties {
    baseUrl = blankToDefault(baseUrl, DEFAULT_BASE_URL);
    model = blankToDefault(model, DEFAULT_MODEL);
    apiKey = apiKey == null ? "" : apiKey;
  }

  /** Default-config convenience constructor: no key configured, safe defaults throughout. */
  public GroqSummarizerProperties() {
    this(DEFAULT_BASE_URL, "", DEFAULT_MODEL);
  }

  public boolean hasApiKey() {
    return !apiKey.isBlank();
  }

  private static String blankToDefault(String value, String defaultValue) {
    return (value == null || value.isBlank()) ? defaultValue : value;
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(GroqSummarizerProperties.class)
  static class Registration {
  }
}
