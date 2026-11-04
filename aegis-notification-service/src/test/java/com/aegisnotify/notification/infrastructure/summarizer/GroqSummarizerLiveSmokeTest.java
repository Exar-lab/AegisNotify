package com.aegisnotify.notification.infrastructure.summarizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.application.dto.SummarizationRequest;
import com.aegisnotify.notification.application.dto.SummarizedContent;
import com.aegisnotify.notification.domain.enums.Channel;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Opt-in live smoke check of the real Groq call. It is skipped unless the
 * {@code AGGREGATION_LLM_GROQ_API_KEY} environment variable is set, so normal
 * and CI runs never reach the network. The key is read only by the adapter
 * from the environment; it is never hardcoded, printed or asserted on.
 *
 * <p>Run it with (see docs/aggregation.md):
 * {@code AGGREGATION_LLM_GROQ_API_KEY=<key> ./mvnw -pl aegis-notification-service test
 * -Dtest=GroqSummarizerLiveSmokeTest}. Optional overrides:
 * {@code AGGREGATION_LLM_GROQ_MODEL}, {@code AGGREGATION_LLM_GROQ_BASE_URL}.</p>
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "AGGREGATION_LLM_GROQ_API_KEY", matches = ".+")
class GroqSummarizerLiveSmokeTest {

  private static final int MAX_OUTPUT_CHARS = 2000;

  @Test
  void summarize_realGroqCall_returnsNonEmptyBodyAndSubjectWithinLimits() {
    // Blank/unset base URL and model fall back to the production defaults.
    GroqSummarizerProperties groqProperties = new GroqSummarizerProperties(
        System.getenv("AGGREGATION_LLM_GROQ_BASE_URL"),
        System.getenv("AGGREGATION_LLM_GROQ_API_KEY"),
        System.getenv("AGGREGATION_LLM_GROQ_MODEL"));
    SummarizerProperties summarizerProperties = new SummarizerProperties(
        "https://unused.example", "", "unused-model", "2023-06-01", 512,
        Duration.ofSeconds(20), MAX_OUTPUT_CHARS);
    GroqChatCompletionsSummarizerAdapter adapter = new GroqChatCompletionsSummarizerAdapter(
        WebClient.builder(), summarizerProperties, groqProperties,
        CircuitBreakerRegistry.ofDefaults());

    SummarizedContent result = adapter.summarize(new SummarizationRequest(
        Channel.EMAIL, "order-update",
        List.of(
            "Your order #1042 has been packed and will ship tomorrow.",
            "Your order #1042 has left our warehouse.",
            "Your order #1042 is out for delivery today between 2pm and 6pm."),
        480));

    assertThat(result).isNotNull();
    assertThat(result.body()).isNotBlank();
    assertThat(result.body().length()).isLessThanOrEqualTo(MAX_OUTPUT_CHARS);
    assertThat(result.subject()).isNotBlank();
    assertThat(result.subject().length())
        .isLessThanOrEqualTo(SummarizerCallTemplate.MAX_SUBJECT_CHARS);
  }
}
