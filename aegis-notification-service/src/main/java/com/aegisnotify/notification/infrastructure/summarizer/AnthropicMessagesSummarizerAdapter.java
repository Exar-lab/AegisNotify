package com.aegisnotify.notification.infrastructure.summarizer;

import com.aegisnotify.notification.application.dto.SummarizationRequest;
import com.aegisnotify.notification.application.dto.SummarizedContent;
import com.aegisnotify.notification.application.port.out.AggregationSummarizerPort;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.exception.SummarizerUnavailableException;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Default LLM-backed {@link AggregationSummarizerPort} implementation (L1 of
 * the design, {@code notification.aggregation.summarizer.vendor=anthropic}):
 * a plain {@link WebClient}-based call to the Anthropic Messages API (no
 * vendor SDK dependency), wrapped in the {@code aggregation-agent} circuit
 * breaker (L2). Only one summarizer vendor is wired at a time, so there is
 * no second summarizer to fail over to.
 *
 * <p>Every failure mode collapses to {@link SummarizerUnavailableException}:
 * an HTTP error status, a network failure, a request timeout (bounded by
 * {@link SummarizerProperties#timeout()}), an open circuit breaker (zero
 * HTTP requests attempted), or output this adapter cannot parse/trust
 * (missing content, blank body, unparseable JSON). Never returns {@code
 * null}, never throws anything else — {@code
 * FlushAggregationWindowsService}'s only reaction to this exception is to
 * release the whole group to individual delivery (never-drop guarantee).</p>
 *
 * <p>The summarized body is escaped for {@link Channel#EMAIL} and
 * length-capped to {@link SummarizerProperties#maxOutputChars()} before
 * being returned (L4) — this is the one place aggregate content bypasses
 * Mustache's own escaping (D9), so nothing downstream re-escapes it. The
 * breaker, failure collapsing and escape-then-cap logic live in {@link
 * SummarizerCallTemplate}, shared with {@link
 * GroqChatCompletionsSummarizerAdapter}; this class owns only the Anthropic
 * request/response shape.</p>
 */
public final class AnthropicMessagesSummarizerAdapter implements AggregationSummarizerPort {

  private static final String MESSAGES_PATH = "/v1/messages";

  private final WebClient webClient;
  private final SummarizerProperties properties;
  private final SummarizerCallTemplate callTemplate;

  public AnthropicMessagesSummarizerAdapter(WebClient.Builder webClientBuilder,
      SummarizerProperties properties, CircuitBreakerRegistry circuitBreakerRegistry) {
    this.properties = properties;
    this.callTemplate = new SummarizerCallTemplate(
        circuitBreakerRegistry, properties.maxOutputChars());
    this.webClient = SummarizerCallTemplate
        .timeoutBoundClient(webClientBuilder, properties.baseUrl(), properties.timeout())
        .defaultHeader("x-api-key", properties.apiKey())
        .defaultHeader("anthropic-version", properties.anthropicVersion())
        .build();
  }

  @Override
  public SummarizedContent summarize(SummarizationRequest request) {
    return callTemplate.summarize(request, this::complete);
  }

  private String complete(SummarizationRequest request) {
    AnthropicMessageRequest body = new AnthropicMessageRequest(
        properties.model(), properties.maxTokens(), SummarizerCallTemplate.systemPrompt(request),
        List.of(new AnthropicMessage("user", SummarizerCallTemplate.buildUserContent(request))));
    AnthropicMessageResponse response = webClient.post()
        .uri(MESSAGES_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToMono(AnthropicMessageResponse.class)
        .block();

    if (response == null || response.content() == null || response.content().isEmpty()) {
      throw new SummarizerUnavailableException("Summarizer returned no content");
    }
    return response.content().get(0).text();
  }

  private record AnthropicMessageRequest(
      String model,
      @JsonProperty("max_tokens") int maxTokens,
      String system,
      List<AnthropicMessage> messages) {
  }

  private record AnthropicMessage(String role, String content) {
  }

  private record AnthropicMessageResponse(List<AnthropicContentBlock> content) {
  }

  private record AnthropicContentBlock(String type, String text) {
  }
}
