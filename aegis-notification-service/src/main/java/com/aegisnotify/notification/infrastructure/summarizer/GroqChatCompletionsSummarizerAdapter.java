package com.aegisnotify.notification.infrastructure.summarizer;

import com.aegisnotify.notification.application.dto.SummarizationRequest;
import com.aegisnotify.notification.application.dto.SummarizedContent;
import com.aegisnotify.notification.application.port.out.AggregationSummarizerPort;
import com.aegisnotify.notification.domain.exception.SummarizerUnavailableException;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Alternate LLM-backed {@link AggregationSummarizerPort} selected by {@code
 * notification.aggregation.summarizer.vendor=groq}: a plain {@link
 * WebClient} call to Groq's OpenAI-compatible Chat Completions API, Bearer
 * authenticated with {@link GroqSummarizerProperties#apiKey()}.
 *
 * <p>Requests JSON mode ({@code response_format: json_object}), so Groq
 * itself guarantees syntactically valid JSON output instead of relying on
 * the system prompt alone. The output is still parsed and trusted exactly as
 * the Anthropic adapter's is.</p>
 *
 * <p>Uses the same {@code aggregation-agent} circuit breaker, failure
 * collapsing to {@link SummarizerUnavailableException}, and escape-then-cap
 * logic as {@link AnthropicMessagesSummarizerAdapter}, via the shared {@link
 * SummarizerCallTemplate}; only one vendor is wired at a time, so one
 * breaker name is the single summarizer circuit operators see. Timeout and
 * output caps come from the vendor-agnostic {@link SummarizerProperties}.</p>
 */
public final class GroqChatCompletionsSummarizerAdapter implements AggregationSummarizerPort {

  private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
  private static final ResponseFormat JSON_OBJECT_FORMAT = new ResponseFormat("json_object");

  private final WebClient webClient;
  private final SummarizerProperties properties;
  private final GroqSummarizerProperties groqProperties;
  private final SummarizerCallTemplate callTemplate;

  public GroqChatCompletionsSummarizerAdapter(WebClient.Builder webClientBuilder,
      SummarizerProperties properties, GroqSummarizerProperties groqProperties,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    this.properties = properties;
    this.groqProperties = groqProperties;
    this.callTemplate = new SummarizerCallTemplate(
        circuitBreakerRegistry, properties.maxOutputChars());
    this.webClient = SummarizerCallTemplate
        .timeoutBoundClient(webClientBuilder, groqProperties.baseUrl(), properties.timeout())
        .defaultHeaders(headers -> headers.setBearerAuth(groqProperties.apiKey()))
        .build();
  }

  @Override
  public SummarizedContent summarize(SummarizationRequest request) {
    return callTemplate.summarize(request, this::complete);
  }

  private String complete(SummarizationRequest request) {
    ChatCompletionRequest body = new ChatCompletionRequest(
        groqProperties.model(),
        List.of(new ChatMessage("system", SummarizerCallTemplate.SYSTEM_PROMPT),
            new ChatMessage("user", SummarizerCallTemplate.buildUserContent(request))),
        properties.maxTokens(),
        JSON_OBJECT_FORMAT);
    ChatCompletionResponse response = webClient.post()
        .uri(CHAT_COMPLETIONS_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToMono(ChatCompletionResponse.class)
        .block();

    if (response == null || response.choices() == null || response.choices().isEmpty()
        || response.choices().get(0).message() == null) {
      throw new SummarizerUnavailableException("Summarizer returned no choices");
    }
    return response.choices().get(0).message().content();
  }

  private record ChatCompletionRequest(
      String model,
      List<ChatMessage> messages,
      @JsonProperty("max_completion_tokens") int maxCompletionTokens,
      @JsonProperty("response_format") ResponseFormat responseFormat) {
  }

  private record ChatMessage(String role, String content) {
  }

  private record ResponseFormat(String type) {
  }

  private record ChatCompletionResponse(List<Choice> choices) {
  }

  private record Choice(ChatMessage message) {
  }
}
