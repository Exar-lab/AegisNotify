package com.aegisnotify.notification.infrastructure.summarizer;

import com.aegisnotify.notification.application.dto.SummarizationRequest;
import com.aegisnotify.notification.application.dto.SummarizedContent;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.exception.SummarizerUnavailableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.HtmlUtils;
import reactor.netty.http.client.HttpClient;

/**
 * Vendor-agnostic shell shared by the LLM-backed summarizer adapters
 * (Anthropic Messages, Groq Chat Completions), so the resilience and
 * output-trust logic is not duplicated per vendor. Each adapter supplies only
 * its vendor-specific HTTP call, returning the raw completion text; this
 * class owns everything around it:
 *
 * <ul>
 *   <li>the single {@code aggregation-agent} circuit breaker — one logical
 *       summarizer circuit from the operator's point of view, whichever
 *       vendor is active, since only one vendor is ever wired at a time;</li>
 *   <li>collapsing every failure (HTTP error, network failure, timeout, open
 *       breaker, untrusted output) to {@link SummarizerUnavailableException};</li>
 *   <li>parsing the {@code {"subject","body"}} payload, then escaping for
 *       {@link Channel#EMAIL} BEFORE capping to {@code max-output-chars}
 *       (L4);</li>
 *   <li>the shared system prompt and user-content rendering, so both vendors
 *       receive identical instructions.</li>
 * </ul>
 *
 * <p>Request/response JSON shapes are deliberately NOT shared: they differ
 * genuinely between vendors and stay private to each adapter.</p>
 */
final class SummarizerCallTemplate {

  static final String CIRCUIT_BREAKER_NAME = "aggregation-agent";
  static final String SYSTEM_PROMPT =
      "You merge several short notification messages of the same kind, addressed to the same "
          + "recipient, into a single concise summary. Respond ONLY with a compact JSON object "
          + "of the exact shape {\"subject\": string, \"body\": string} and no other text, no "
          + "markdown fences. Keep \"body\" under the requested character limit.";

  private static final Logger log = LoggerFactory.getLogger(SummarizerCallTemplate.class);

  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final int maxOutputChars;
  private final ObjectMapper objectMapper = new ObjectMapper();

  SummarizerCallTemplate(CircuitBreakerRegistry circuitBreakerRegistry, int maxOutputChars) {
    this.circuitBreakerRegistry = circuitBreakerRegistry;
    this.maxOutputChars = maxOutputChars;
  }

  /**
   * Returns a builder pointed at {@code baseUrl} whose response time is
   * bounded by {@code timeout}; callers add their vendor auth headers.
   */
  static WebClient.Builder timeoutBoundClient(WebClient.Builder webClientBuilder, String baseUrl,
      Duration timeout) {
    return webClientBuilder
        .baseUrl(baseUrl)
        .clientConnector(new ReactorClientHttpConnector(
            HttpClient.create().responseTimeout(timeout)));
  }

  /**
   * Runs {@code completionCall} (the vendor HTTP call, returning raw
   * completion text) inside the breaker and turns its output into trusted
   * {@link SummarizedContent}. Never returns {@code null}; throws only
   * {@link SummarizerUnavailableException}.
   */
  SummarizedContent summarize(SummarizationRequest request,
      Function<SummarizationRequest, String> completionCall) {
    CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
    try {
      return circuitBreaker.decorateSupplier(() -> callAndParse(request, completionCall)).get();
    } catch (CallNotPermittedException ex) {
      log.warn("aggregation_summarizer_breaker_open");
      throw new SummarizerUnavailableException("aggregation-agent circuit breaker is open", ex);
    }
  }

  private SummarizedContent callAndParse(SummarizationRequest request,
      Function<SummarizationRequest, String> completionCall) {
    try {
      String text = completionCall.apply(request);
      if (text == null || text.isBlank()) {
        throw new SummarizerUnavailableException("Summarizer returned blank content");
      }
      SummaryPayload payload = parsePayload(text);
      // Bug fix (review-risk WARNING / sdd-verify): escape BEFORE capping,
      // not after. HTML-escaping can expand a string (e.g. "<" -> "&lt;",
      // 1 char -> 4 chars), so capping first could let the post-escape
      // EMAIL body exceed max-output-chars. Capping the already-escaped
      // result guarantees the final stored/delivered body never exceeds it.
      String escapedBody = request.channel() == Channel.EMAIL
          ? HtmlUtils.htmlEscape(payload.body()) : payload.body();
      String finalBody = capLength(escapedBody, maxOutputChars);

      return new SummarizedContent(payload.subject(), finalBody);
    } catch (SummarizerUnavailableException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      log.warn("aggregation_summarizer_call_failed reason={}", ex.getMessage());
      throw new SummarizerUnavailableException("Summarizer call failed", ex);
    }
  }

  static String buildUserContent(SummarizationRequest request) {
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

  private static String capLength(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private record SummaryPayload(String subject, String body) {
  }
}
