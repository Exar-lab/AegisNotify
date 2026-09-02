package com.aegisnotify.notification.infrastructure.summarizer;

import com.aegisnotify.notification.application.dto.SummarizationRequest;
import com.aegisnotify.notification.application.dto.SummarizedContent;
import com.aegisnotify.notification.application.port.out.AggregationSummarizerPort;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.exception.SummarizerUnavailableException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.HtmlUtils;
import reactor.netty.http.client.HttpClient;

/**
 * Sole LLM-backed {@link AggregationSummarizerPort} implementation (L1 of the
 * design): a plain {@link WebClient}-based call to the Anthropic Messages API
 * (no vendor SDK dependency), wrapped in the {@code aggregation-agent}
 * circuit breaker (L2) folded directly into this adapter — there is no
 * second summarizer to fail over to, so a separate resilient-decorator class
 * would add nothing.
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
 * Mustache's own escaping (D9), so nothing downstream re-escapes it.</p>
 */
public final class AnthropicMessagesSummarizerAdapter implements AggregationSummarizerPort {

  private static final Logger log =
      LoggerFactory.getLogger(AnthropicMessagesSummarizerAdapter.class);
  private static final String MESSAGES_PATH = "/v1/messages";
  private static final String CIRCUIT_BREAKER_NAME = "aggregation-agent";
  private static final String SYSTEM_PROMPT =
      "You merge several short notification messages of the same kind, addressed to the same "
          + "recipient, into a single concise summary. Respond ONLY with a compact JSON object "
          + "of the exact shape {\"subject\": string, \"body\": string} and no other text, no "
          + "markdown fences. Keep \"body\" under the requested character limit.";

  private final WebClient webClient;
  private final SummarizerProperties properties;
  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public AnthropicMessagesSummarizerAdapter(WebClient.Builder webClientBuilder,
      SummarizerProperties properties, CircuitBreakerRegistry circuitBreakerRegistry) {
    this.properties = properties;
    this.circuitBreakerRegistry = circuitBreakerRegistry;
    this.webClient = webClientBuilder
        .baseUrl(properties.baseUrl())
        .defaultHeader("x-api-key", properties.apiKey())
        .defaultHeader("anthropic-version", properties.anthropicVersion())
        .clientConnector(new ReactorClientHttpConnector(
            HttpClient.create().responseTimeout(properties.timeout())))
        .build();
  }

  @Override
  public SummarizedContent summarize(SummarizationRequest request) {
    CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
    try {
      return circuitBreaker.decorateSupplier(() -> callAndParse(request)).get();
    } catch (CallNotPermittedException ex) {
      log.warn("aggregation_summarizer_breaker_open");
      throw new SummarizerUnavailableException("aggregation-agent circuit breaker is open", ex);
    }
  }

  private SummarizedContent callAndParse(SummarizationRequest request) {
    try {
      AnthropicMessageRequest body = buildRequest(request);
      AnthropicMessageResponse response = webClient.post()
          .uri(MESSAGES_PATH)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(body)
          .retrieve()
          .bodyToMono(AnthropicMessageResponse.class)
          .block();

      String text = extractText(response);
      SummaryPayload payload = parsePayload(text);
      // Bug fix (review-risk WARNING / sdd-verify): escape BEFORE capping,
      // not after. HTML-escaping can expand a string (e.g. "<" -> "&lt;",
      // 1 char -> 4 chars), so capping first could let the post-escape
      // EMAIL body exceed max-output-chars. Capping the already-escaped
      // result guarantees the final stored/delivered body never exceeds it.
      String escapedBody = request.channel() == Channel.EMAIL
          ? HtmlUtils.htmlEscape(payload.body()) : payload.body();
      String finalBody = capLength(escapedBody, properties.maxOutputChars());

      return new SummarizedContent(payload.subject(), finalBody);
    } catch (SummarizerUnavailableException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      log.warn("aggregation_summarizer_call_failed reason={}", ex.getMessage());
      throw new SummarizerUnavailableException("Summarizer call failed", ex);
    }
  }

  private AnthropicMessageRequest buildRequest(SummarizationRequest request) {
    String userContent = buildUserContent(request);
    return new AnthropicMessageRequest(properties.model(), properties.maxTokens(), SYSTEM_PROMPT,
        List.of(new AnthropicMessage("user", userContent)));
  }

  private String buildUserContent(SummarizationRequest request) {
    StringBuilder builder = new StringBuilder();
    builder.append("Channel: ").append(request.channel()).append('\n');
    if (request.templateName() != null) {
      builder.append("Template: ").append(request.templateName()).append('\n');
    }
    builder.append("Character limit for body: ").append(request.maxLength()).append('\n');
    builder.append("Messages to merge:\n");
    List<String> bodies = request.renderedBodies();
    for (int i = 0; i < bodies.size(); i++) {
      builder.append(i + 1).append(". ").append(bodies.get(i)).append('\n');
    }
    return builder.toString();
  }

  private String extractText(AnthropicMessageResponse response) {
    if (response == null || response.content() == null || response.content().isEmpty()) {
      throw new SummarizerUnavailableException("Summarizer returned no content");
    }
    String text = response.content().get(0).text();
    if (text == null || text.isBlank()) {
      throw new SummarizerUnavailableException("Summarizer returned blank content");
    }
    return text;
  }

  private SummaryPayload parsePayload(String text) {
    SummaryPayload payload;
    try {
      payload = objectMapper.readValue(text.trim(), SummaryPayload.class);
    } catch (JsonProcessingException ex) {
      throw new SummarizerUnavailableException("Summarizer returned unparseable output", ex);
    }
    if (payload == null || payload.body() == null || payload.body().isBlank()) {
      throw new SummarizerUnavailableException("Summarizer produced an empty body");
    }
    return payload;
  }

  private String capLength(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
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

  private record SummaryPayload(String subject, String body) {
  }
}
