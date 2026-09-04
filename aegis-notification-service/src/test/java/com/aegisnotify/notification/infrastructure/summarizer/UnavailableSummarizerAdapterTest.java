package com.aegisnotify.notification.infrastructure.summarizer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aegisnotify.notification.application.dto.SummarizationRequest;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.exception.SummarizerUnavailableException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link UnavailableSummarizerAdapter} (L3 of the design): the safe
 * default when aggregation is disabled or no API key is configured must
 * always throw, never return, so callers always take the individual-delivery
 * fallback path.
 */
class UnavailableSummarizerAdapterTest {

  private final UnavailableSummarizerAdapter adapter = new UnavailableSummarizerAdapter();

  @Test
  void summarize_alwaysThrowsSummarizerUnavailableException() {
    SummarizationRequest request =
        new SummarizationRequest(Channel.EMAIL, "welcome", List.of("body1", "body2"), 500);

    assertThatThrownBy(() -> adapter.summarize(request))
        .isInstanceOf(SummarizerUnavailableException.class);
  }
}
