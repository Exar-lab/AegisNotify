package com.aegisnotify.notification.infrastructure.provider;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.dto.ProviderResult.Outcome;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.domain.enums.Channel;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Vonage-based adapter for WhatsApp delivery, called by {@link
 * NotificationProviderRouter} for {@link Channel#WHATSAPP}.
 *
 * <p>Sends a {@code text} message via Vonage's Messages API ({@code POST
 * /v1/messages}). The supplied {@link WebClient} must already carry the base
 * URL and HTTP Basic auth ({@code api_key:api_secret}). Vonage expects the
 * recipient as bare digits starting with the country code, so the leading
 * {@code +} of an E.164 recipient is stripped. Never returns
 * {@code SENT_VIA_FALLBACK} or {@code FAILED_CRITICAL} — those outcomes
 * belong to a resilience/failover layer, not to an individual adapter.</p>
 */
public class VonageWhatsAppProviderAdapter implements NotificationProviderPort {

  private static final Logger log = LoggerFactory.getLogger(VonageWhatsAppProviderAdapter.class);
  private static final String PROVIDER_NAME = "Vonage";
  private static final String CHANNEL_WHATSAPP = "whatsapp";
  private static final String MESSAGE_TYPE_TEXT = "text";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private final WebClient webClient;
  private final String fromNumber;
  private final ProviderMetrics metrics;

  public VonageWhatsAppProviderAdapter(WebClient webClient, String fromNumber,
      MeterRegistry meterRegistry) {
    this.webClient = webClient;
    this.fromNumber = fromNumber;
    this.metrics = new ProviderMetrics(meterRegistry);
  }

  @Override
  public ProviderResult send(Channel channel, String recipient, String renderedContent,
      String subject) {
    VonageMessageRequest request = new VonageMessageRequest(
        fromNumber, stripLeadingPlus(recipient), MESSAGE_TYPE_TEXT, renderedContent,
        CHANNEL_WHATSAPP);

    Timer.Sample sample = metrics.startTimer();
    try {
      var response = webClient.post()
          .uri("/v1/messages")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .retrieve()
          .toBodilessEntity()
          .timeout(REQUEST_TIMEOUT)
          .block();
      return new ProviderResult(Outcome.SENT, PROVIDER_NAME, null).withCode(
          String.valueOf(response.getStatusCode().value()));
    } catch (WebClientResponseException ex) {
      log.warn("vonage_send_failed status={} body={}", ex.getStatusCode(),
          ex.getResponseBodyAsString());
      metrics.recordError(PROVIDER_NAME, String.valueOf(ex.getStatusCode().value()));
      return new ProviderResult(Outcome.FAILED, PROVIDER_NAME, ex.getMessage(),
          RetryClassification.isRetryable(ex.getStatusCode()))
          .withCode(String.valueOf(ex.getStatusCode().value()));
    } catch (Exception ex) {
      log.warn("vonage_send_failed error={}", ex.getMessage());
      metrics.recordError(PROVIDER_NAME, ex.getClass().getSimpleName());
      return new ProviderResult(Outcome.FAILED, PROVIDER_NAME, ex.getMessage(),
          RetryClassification.isRetryable(ex)).withCode(ex.getClass().getSimpleName());
    } finally {
      metrics.stopTimer(sample, PROVIDER_NAME);
    }
  }

  private static String stripLeadingPlus(String recipient) {
    return recipient != null && recipient.startsWith("+") ? recipient.substring(1) : recipient;
  }

  private record VonageMessageRequest(
      String from,
      String to,
      @JsonProperty("message_type") String messageType,
      String text,
      String channel) {
  }
}
