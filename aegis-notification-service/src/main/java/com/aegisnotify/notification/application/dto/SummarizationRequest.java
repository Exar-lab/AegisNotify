package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.enums.Channel;
import java.util.List;

/**
 * Input to {@link com.aegisnotify.notification.application.port.out
 * .AggregationSummarizerPort#summarize}. Deliberately carries only rendered
 * bodies — no recipient address, no raw parameter map, no notification ids
 * (D4 of the design). This is the enforced data-scope boundary between the
 * aggregation buffer and the third-party LLM call: whatever PII a template's
 * parameters may have carried is already baked into (or absent from) the
 * rendered text by the time it reaches this record, and nothing else about
 * the notification is available to leak.
 *
 * @param channel        the shared channel of every notification in the
 *                        group
 * @param templateName   the shared template name, or {@code null} when
 *                        {@code require-same-template} is disabled (D12)
 * @param renderedBodies the rendered body of every notification being
 *                        folded into this summary
 * @param maxLength      a soft hint to the summarizer on the desired output
 *                        length; the summarizer's own configured
 *                        max-output-chars is the actually enforced cap (L4)
 */
public record SummarizationRequest(
    Channel channel,
    String templateName,
    List<String> renderedBodies,
    int maxLength) {

  public SummarizationRequest {
    renderedBodies = renderedBodies == null ? List.of() : List.copyOf(renderedBodies);
  }
}
