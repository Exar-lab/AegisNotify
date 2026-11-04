package com.aegisnotify.notification.infrastructure.summarizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aegisnotify.notification.application.dto.SummarizationRequest;
import com.aegisnotify.notification.application.dto.SummarizedContent;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.exception.SummarizerUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Covers {@link GroqChatCompletionsSummarizerAdapter} against a real HTTP
 * server (MockWebServer), mirroring {@code
 * AnthropicMessagesSummarizerAdapterTest}: the OpenAI-style request shape
 * (Bearer auth, system+user messages, {@code max_completion_tokens}, JSON
 * mode), success parsing from {@code choices[0].message.content}, every
 * failure mode collapsing to {@link SummarizerUnavailableException}, the
 * shared {@code aggregation-agent} breaker short-circuiting with zero HTTP
 * requests, and L4's escape-then-cap behavior.
 */
class GroqChatCompletionsSummarizerAdapterTest {

  private static final String CIRCUIT_BREAKER_NAME = "aggregation-agent";

  private MockWebServer server;
  private CircuitBreakerRegistry circuitBreakerRegistry;

  @BeforeEach
  void startServer() throws IOException {
    server = new MockWebServer();
    server.start();
    CircuitBreakerConfig config = CircuitBreakerConfig.custom()
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(2)
        .minimumNumberOfCalls(2)
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofMinutes(1))
        .permittedNumberOfCallsInHalfOpenState(1)
        .automaticTransitionFromOpenToHalfOpenEnabled(false)
        .build();
    circuitBreakerRegistry = CircuitBreakerRegistry.of(config);
  }

  @AfterEach
  void stopServer() throws IOException {
    server.shutdown();
  }

  private GroqChatCompletionsSummarizerAdapter adapter(Duration timeout, int maxOutputChars) {
    SummarizerProperties summarizerProperties = new SummarizerProperties(
        "https://unused.example", "", "unused-model", "2023-06-01", 512, timeout,
        maxOutputChars);
    GroqSummarizerProperties groqProperties = new GroqSummarizerProperties(
        "http://localhost:" + server.getPort(), "groq-test-key", "llama-3.1-8b-instant");
    return new GroqChatCompletionsSummarizerAdapter(
        WebClient.builder(), summarizerProperties, groqProperties, circuitBreakerRegistry);
  }

  private GroqChatCompletionsSummarizerAdapter adapter() {
    return adapter(Duration.ofSeconds(5), 2000);
  }

  private SummarizationRequest requestWith(Channel channel) {
    return new SummarizationRequest(
        channel, "welcome", List.of("Body one.", "Body two."), 500);
  }

  private static MockResponse completionWithContent(String escapedContentJson) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"choices\":[{\"index\":0,"
            + "\"message\":{\"role\":\"assistant\",\"content\":" + escapedContentJson + "},"
            + "\"finish_reason\":\"stop\"}]}");
  }

  @Test
  void summarize_success_returnsParsedSubjectAndBody() throws InterruptedException {
    server.enqueue(completionWithContent(
        "\"{\\\"subject\\\":\\\"Update\\\",\\\"body\\\":\\\"Two things happened.\\\"}\""));

    SummarizedContent result = adapter().summarize(requestWith(Channel.SMS));

    assertThat(result.subject()).isEqualTo("Update");
    assertThat(result.body()).isEqualTo("Two things happened.");

    RecordedRequest recorded = server.takeRequest();
    assertThat(recorded.getPath()).isEqualTo("/v1/chat/completions");
    assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer groq-test-key");
    assertThat(recorded.getHeader("x-api-key")).isNull();
    assertThat(recorded.getHeader("anthropic-version")).isNull();
  }

  @Test
  void summarize_requestBody_usesChatCompletionsShapeWithJsonMode() throws Exception {
    server.enqueue(completionWithContent(
        "\"{\\\"subject\\\":\\\"S\\\",\\\"body\\\":\\\"B\\\"}\""));

    adapter().summarize(requestWith(Channel.SMS));

    JsonNode body = new ObjectMapper().readTree(server.takeRequest().getBody().readUtf8());
    assertThat(body.get("model").asText()).isEqualTo("llama-3.1-8b-instant");
    assertThat(body.get("max_completion_tokens").asInt()).isEqualTo(512);
    assertThat(body.has("max_tokens")).isFalse();
    assertThat(body.has("system")).isFalse();
    assertThat(body.get("response_format").get("type").asText()).isEqualTo("json_object");
    JsonNode messages = body.get("messages");
    assertThat(messages).hasSize(2);
    assertThat(messages.get(0).get("role").asText()).isEqualTo("system");
    assertThat(messages.get(0).get("content").asText())
        .isEqualTo(SummarizerCallTemplate.SYSTEM_PROMPT);
    assertThat(messages.get(1).get("role").asText()).isEqualTo("user");
    assertThat(messages.get(1).get("content").asText())
        .contains("Body one.").contains("Body two.");
  }

  @Test
  void summarize_requestBody_excludesRecipientParametersAndIds() throws InterruptedException {
    server.enqueue(completionWithContent(
        "\"{\\\"subject\\\":\\\"S\\\",\\\"body\\\":\\\"B\\\"}\""));

    adapter().summarize(requestWith(Channel.SMS));

    String body = server.takeRequest().getBody().readUtf8();
    assertThat(body).doesNotContainPattern("\\+\\d{6,}");
    assertThat(body).doesNotContain("@example.com");
    assertThat(body).doesNotContain("\"parameters\"");
    assertThat(body).doesNotContain("\"recipient\"");
  }

  @Test
  void summarize_emailChannel_bodyIsHtmlEscaped() {
    server.enqueue(completionWithContent(
        "\"{\\\"subject\\\":\\\"S\\\",\\\"body\\\":\\\"<script>alert(1)</script>\\\"}\""));

    SummarizedContent result = adapter().summarize(requestWith(Channel.EMAIL));

    assertThat(result.body()).doesNotContain("<script>");
    assertThat(result.body()).contains("&lt;script&gt;");
  }

  @Test
  void summarize_nonEmailChannel_bodyNotEscaped() {
    server.enqueue(completionWithContent(
        "\"{\\\"subject\\\":\\\"S\\\",\\\"body\\\":\\\"A & B\\\"}\""));

    SummarizedContent result = adapter().summarize(requestWith(Channel.SMS));

    assertThat(result.body()).isEqualTo("A & B");
  }

  @Test
  void summarize_bodyLongerThanMaxOutputChars_isCapped() {
    server.enqueue(completionWithContent(
        "\"{\\\"subject\\\":\\\"S\\\",\\\"body\\\":\\\"0123456789\\\"}\""));

    SummarizedContent result =
        adapter(Duration.ofSeconds(5), 5).summarize(requestWith(Channel.SMS));

    assertThat(result.body()).isEqualTo("01234");
  }

  @Test
  void summarize_subjectWithLineBreaks_isCollapsedToSingleLine() {
    server.enqueue(completionWithContent(
        "\"{\\\"subject\\\":\\\"  Hello\\\\r\\\\nBcc: x@evil.com  \\\",\\\"body\\\":\\\"B\\\"}\""));

    SummarizedContent result = adapter().summarize(requestWith(Channel.EMAIL));

    assertThat(result.subject()).isEqualTo("Hello Bcc: x@evil.com");
  }

  @Test
  void summarize_subjectLongerThanLimit_isCappedAt255() {
    String longSubject = "s".repeat(400);
    server.enqueue(completionWithContent(
        "\"{\\\"subject\\\":\\\"" + longSubject + "\\\",\\\"body\\\":\\\"B\\\"}\""));

    SummarizedContent result = adapter().summarize(requestWith(Channel.SMS));

    assertThat(result.subject()).hasSize(255);
  }

  @Test
  void summarize_blankOrMissingSubject_isReturnedAsNull() {
    server.enqueue(completionWithContent(
        "\"{\\\"subject\\\":\\\"   \\\",\\\"body\\\":\\\"B\\\"}\""));
    server.enqueue(completionWithContent("\"{\\\"body\\\":\\\"B\\\"}\""));

    assertThat(adapter().summarize(requestWith(Channel.SMS)).subject()).isNull();
    assertThat(adapter().summarize(requestWith(Channel.SMS)).subject()).isNull();
  }

  /**
   * Same escape-then-cap ordering guarantee as the Anthropic adapter, proven
   * here for Groq rather than assumed: "<<<<<" escapes to 20 chars, so a
   * cap-then-escape order would return a body longer than the 10-char cap.
   */
  @Test
  void summarize_emailChannel_finalEscapedBodyNeverExceedsMaxOutputChars() {
    server.enqueue(completionWithContent(
        "\"{\\\"subject\\\":\\\"S\\\",\\\"body\\\":\\\"<<<<<\\\"}\""));

    int maxOutputChars = 10;
    SummarizedContent result =
        adapter(Duration.ofSeconds(5), maxOutputChars).summarize(requestWith(Channel.EMAIL));

    assertThat(result.body().length()).isLessThanOrEqualTo(maxOutputChars);
    assertThat(result.body()).doesNotContain("<");
  }

  @Test
  void summarize_serverError_throwsSummarizerUnavailableException() {
    server.enqueue(new MockResponse()
        .setResponseCode(500)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"error\":{\"message\":\"internal\",\"type\":\"server_error\"}}"));

    assertThatThrownBy(() -> adapter().summarize(requestWith(Channel.SMS)))
        .isInstanceOf(SummarizerUnavailableException.class);
  }

  @Test
  void summarize_rateLimited_throwsSummarizerUnavailableException() {
    server.enqueue(new MockResponse().setResponseCode(429));

    assertThatThrownBy(() -> adapter().summarize(requestWith(Channel.SMS)))
        .isInstanceOf(SummarizerUnavailableException.class);
  }

  @Test
  void summarize_malformedOuterJson_throwsSummarizerUnavailableException() {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("not json at all"));

    assertThatThrownBy(() -> adapter().summarize(requestWith(Channel.SMS)))
        .isInstanceOf(SummarizerUnavailableException.class);
  }

  @Test
  void summarize_malformedInnerJson_throwsSummarizerUnavailableException() {
    server.enqueue(completionWithContent("\"not the expected json shape\""));

    assertThatThrownBy(() -> adapter().summarize(requestWith(Channel.SMS)))
        .isInstanceOf(SummarizerUnavailableException.class);
  }

  @Test
  void summarize_blankContent_throwsSummarizerUnavailableException() {
    server.enqueue(completionWithContent("\"   \""));

    assertThatThrownBy(() -> adapter().summarize(requestWith(Channel.SMS)))
        .isInstanceOf(SummarizerUnavailableException.class);
  }

  @Test
  void summarize_nullContent_throwsSummarizerUnavailableException() {
    server.enqueue(completionWithContent("null"));

    assertThatThrownBy(() -> adapter().summarize(requestWith(Channel.SMS)))
        .isInstanceOf(SummarizerUnavailableException.class);
  }

  @Test
  void summarize_emptyChoicesArray_throwsSummarizerUnavailableException() {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"choices\":[]}"));

    assertThatThrownBy(() -> adapter().summarize(requestWith(Channel.SMS)))
        .isInstanceOf(SummarizerUnavailableException.class);
  }

  @Test
  void summarize_serverNeverResponds_boundedByConfiguredTimeout() {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

    GroqChatCompletionsSummarizerAdapter shortTimeoutAdapter =
        adapter(Duration.ofMillis(200), 2000);
    assertThatThrownBy(() -> shortTimeoutAdapter.summarize(requestWith(Channel.SMS)))
        .isInstanceOf(SummarizerUnavailableException.class);
  }

  @Test
  void summarize_repeatedFailures_opensSharedBreakerAndStopsCallingServer()
      throws InterruptedException {
    server.enqueue(new MockResponse().setResponseCode(500));
    server.enqueue(new MockResponse().setResponseCode(500));
    GroqChatCompletionsSummarizerAdapter adapter = adapter();

    assertThatThrownBy(() -> adapter.summarize(requestWith(Channel.SMS)))
        .isInstanceOf(SummarizerUnavailableException.class);
    assertThatThrownBy(() -> adapter.summarize(requestWith(Channel.SMS)))
        .isInstanceOf(SummarizerUnavailableException.class);

    CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

    int requestCountBeforeThirdCall = server.getRequestCount();
    assertThatThrownBy(() -> adapter.summarize(requestWith(Channel.SMS)))
        .isInstanceOf(SummarizerUnavailableException.class);

    assertThat(server.getRequestCount()).isEqualTo(requestCountBeforeThirdCall);
  }
}
