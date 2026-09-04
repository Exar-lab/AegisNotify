package com.aegisnotify.notification.infrastructure.summarizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aegisnotify.notification.application.dto.SummarizationRequest;
import com.aegisnotify.notification.application.dto.SummarizedContent;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.exception.SummarizerUnavailableException;
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
 * Covers {@link AnthropicMessagesSummarizerAdapter} against a real HTTP
 * server (MockWebServer, same pattern as {@code KeycloakAdminClientAdapterTest}
 * from the #74 work): success parsing, every documented failure mode
 * collapsing to {@link SummarizerUnavailableException}, the open-breaker
 * short-circuit making zero HTTP requests, the D4 PII-exclusion assertion on
 * the outgoing request body, and L4's EMAIL-escaping/length-cap behavior.
 */
class AnthropicMessagesSummarizerAdapterTest {

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

  private AnthropicMessagesSummarizerAdapter adapter(Duration timeout, int maxOutputChars) {
    SummarizerProperties properties = new SummarizerProperties(
        "http://localhost:" + server.getPort(), "test-api-key", "claude-sonnet-4-5",
        "2023-06-01", 512, timeout, maxOutputChars);
    return new AnthropicMessagesSummarizerAdapter(
        WebClient.builder(), properties, circuitBreakerRegistry);
  }

  private AnthropicMessagesSummarizerAdapter adapter() {
    return adapter(Duration.ofSeconds(5), 2000);
  }

  private SummarizationRequest requestWith(Channel channel) {
    return new SummarizationRequest(
        channel, "welcome", List.of("Body one.", "Body two."), 500);
  }

  @Test
  void summarize_success_returnsParsedSubjectAndBody() throws InterruptedException {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"content\":[{\"type\":\"text\",\"text\":"
            + "\"{\\\"subject\\\":\\\"Update\\\",\\\"body\\\":\\\"Two things happened.\\\"}\"}]}"));

    SummarizedContent result = adapter().summarize(requestWith(Channel.SMS));

    assertThat(result.subject()).isEqualTo("Update");
    assertThat(result.body()).isEqualTo("Two things happened.");

    RecordedRequest recorded = server.takeRequest();
    assertThat(recorded.getHeader("x-api-key")).isEqualTo("test-api-key");
    assertThat(recorded.getHeader("anthropic-version")).isEqualTo("2023-06-01");
    assertThat(recorded.getPath()).isEqualTo("/v1/messages");
  }

  @Test
  void summarize_requestBody_excludesRecipientParametersAndIds() throws InterruptedException {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"content\":[{\"type\":\"text\",\"text\":"
            + "\"{\\\"subject\\\":\\\"S\\\",\\\"body\\\":\\\"B\\\"}\"}]}"));

    adapter().summarize(requestWith(Channel.SMS));

    RecordedRequest recorded = server.takeRequest();
    String body = recorded.getBody().readUtf8();
    assertThat(body).contains("Body one.").contains("Body two.");
    // SummarizationRequest has no recipient/parameter-map/id fields at all
    // (D4, compile-time enforced) — this assertion proves no concrete PII
    // value leaks into the serialized request body.
    assertThat(body).doesNotContainPattern("\\+\\d{6,}");
    assertThat(body).doesNotContain("@example.com");
    assertThat(body).doesNotContain("\"parameters\"");
    assertThat(body).doesNotContain("\"recipient\"");
  }

  @Test
  void summarize_emailChannel_bodyIsHtmlEscaped() {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"content\":[{\"type\":\"text\",\"text\":"
            + "\"{\\\"subject\\\":\\\"S\\\",\\\"body\\\":\\\"<script>alert(1)</script>\\\"}\"}]}"));

    SummarizedContent result = adapter().summarize(requestWith(Channel.EMAIL));

    assertThat(result.body()).doesNotContain("<script>");
    assertThat(result.body()).contains("&lt;script&gt;");
  }

  @Test
  void summarize_nonEmailChannel_bodyNotEscaped() {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"content\":[{\"type\":\"text\",\"text\":"
            + "\"{\\\"subject\\\":\\\"S\\\",\\\"body\\\":\\\"A & B\\\"}\"}]}"));

    SummarizedContent result = adapter().summarize(requestWith(Channel.SMS));

    assertThat(result.body()).isEqualTo("A & B");
  }

  @Test
  void summarize_bodyLongerThanMaxOutputChars_isCapped() {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"content\":[{\"type\":\"text\",\"text\":"
            + "\"{\\\"subject\\\":\\\"S\\\",\\\"body\\\":\\\"0123456789\\\"}\"}]}"));

    SummarizedContent result =
        adapter(Duration.ofSeconds(5), 5).summarize(requestWith(Channel.SMS));

    assertThat(result.body()).isEqualTo("01234");
  }

  /**
   * Bug fix (review-risk WARNING, also independently found by sdd-verify):
   * capLength used to run BEFORE htmlEscape for EMAIL, and escaping can
   * EXPAND a string ("<" -> "&lt;", 1 char -> 4 chars), so a pre-escape
   * string correctly capped at maxOutputChars could still balloon past it
   * once escaped. This asserts the FINAL stored body — after escaping —
   * never exceeds maxOutputChars, using HTML-special characters near the
   * cap boundary.
   */
  @Test
  void summarize_emailChannel_finalEscapedBodyNeverExceedsMaxOutputChars() {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"content\":[{\"type\":\"text\",\"text\":"
            + "\"{\\\"subject\\\":\\\"S\\\",\\\"body\\\":\\\"<<<<<\\\"}\"}]}"));

    int maxOutputChars = 10;
    SummarizedContent result =
        adapter(Duration.ofSeconds(5), maxOutputChars).summarize(requestWith(Channel.EMAIL));

    assertThat(result.body().length()).isLessThanOrEqualTo(maxOutputChars);
    assertThat(result.body()).doesNotContain("<");
  }

  @Test
  void summarize_serverError_throwsSummarizerUnavailableException() {
    server.enqueue(new MockResponse().setResponseCode(500));

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
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"content\":[{\"type\":\"text\",\"text\":\"not the expected json shape\"}]}"));

    assertThatThrownBy(() -> adapter().summarize(requestWith(Channel.SMS)))
        .isInstanceOf(SummarizerUnavailableException.class);
  }

  @Test
  void summarize_emptyContentArray_throwsSummarizerUnavailableException() {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"content\":[]}"));

    assertThatThrownBy(() -> adapter().summarize(requestWith(Channel.SMS)))
        .isInstanceOf(SummarizerUnavailableException.class);
  }

  @Test
  void summarize_serverNeverResponds_boundedByConfiguredTimeout() {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

    AnthropicMessagesSummarizerAdapter shortTimeoutAdapter = adapter(Duration.ofMillis(200), 2000);
    assertThatThrownBy(() -> shortTimeoutAdapter.summarize(requestWith(Channel.SMS)))
        .isInstanceOf(SummarizerUnavailableException.class);
  }

  @Test
  void summarize_repeatedFailures_opensBreakerAndStopsCallingServer_zeroRequestsWhenOpen()
      throws InterruptedException {
    server.enqueue(new MockResponse().setResponseCode(500));
    server.enqueue(new MockResponse().setResponseCode(500));
    AnthropicMessagesSummarizerAdapter adapter = adapter();

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
